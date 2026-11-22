package app.myownware.perseid.core.download

import android.content.Context
import android.database.SQLException
import androidx.work.ForegroundInfo
import app.myownware.perseid.core.AppContainer
import app.myownware.perseid.core.engine.MediaType
import app.myownware.perseid.core.engine.TrackMerge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlin.coroutines.coroutineContext

internal class DownloadRunner(
    private val context: Context,
    private val container: AppContainer,
    private val postProgress: suspend (ForegroundInfo) -> Unit,
) {
    private val coordinator get() = container.downloads
    private val dao get() = container.downloadDao
    private val notifications = DownloadNotifications(context)

    suspend fun run(
        onInterrupted: suspend () -> Unit,
        jobId: Int? = null,
        finished: () -> Unit = {},
    ): Boolean =
        coordinator.execution.withLock {
            try {
                drain(jobId, finished)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { onInterrupted() }
                throw cancelled
            }
        }

    private suspend fun drain(
        jobId: Int?,
        finished: () -> Unit,
    ): Boolean {
        settleInterruptedActions(context, dao)
        while (true) {
            val job = coordinator.next(jobId, finished) ?: return false
            if (runJob(job)) return true
        }
    }

    private suspend fun runJob(job: DownloadJob): Boolean {
        if (dao.transition(job.id, job.state, JobState.Downloading) != 1) return false
        latestProgress = null
        return try {
            showProgress(notifications.foreground(job, queued = queuedCount(dao.queue().first())))
            withContext(Dispatchers.IO) { requireStorage(context, context.noBackupFilesDir, 1) }
            download(job)
            false
        } catch (cancelled: CancellationException) {
            coroutineContext.ensureActive()
            if (dao.observe(job.id).first()?.state !in UserActions) throw cancelled
            false
        } catch (failure: IOException) {
            fail(job, failure.downloadError())
        } catch (_: PlatformProgressRefused) {
            coordinator.interrupted()
            false
        } catch (_: IllegalStateException) {
            fail(job, DownloadError.Unknown)
        } catch (_: SecurityException) {
            fail(job, DownloadError.Unknown)
        } catch (_: SQLException) {
            fail(job, DownloadError.Save)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { settleUserAction(context, dao, job.id) }
        }
    }

    private suspend fun download(job: DownloadJob) {
        val media = transferUntilCancelled(job) ?: return
        if (dao.beginPublishing(job.id) != 1) return
        val saved = save(job, media)
        withContext(NonCancellable) {
            dao.saved(job.id, saved.uri.toString(), saved.mime, System.currentTimeMillis(), media.length())
            withContext(Dispatchers.IO) { File(job.stagingPath).deleteRecursively() }
            notifications.saved(job, saved)
        }
    }

    private suspend fun transferUntilCancelled(job: DownloadJob): File? =
        coroutineScope {
            val progress = Channel<Progress>(Channel.CONFLATED)
            val updates = launch { for (event in progress) updateProgress(job, event) }
            val queueUpdates =
                launch {
                    dao.queue().map(::queuedCount).distinctUntilChanged().collect { count ->
                        showProgress(notifications.foreground(job, latestProgress, count))
                    }
                }
            val transfer =
                async {
                    transferTracks(container.transferEngine, job, { progress.trySend(it) }) { mode, tracks, folder ->
                        merge(job, mode, tracks, folder)
                    }
                }
            val cancellation = async { dao.observe(job.id).first { it?.state in UserActions } }
            try {
                select {
                    transfer.onAwait { it }
                    cancellation.onAwait { null }
                }
            } finally {
                transfer.cancel()
                cancellation.cancel()
                updates.cancel()
                queueUpdates.cancel()
            }
        }

    private var latestProgress: Progress? = null

    private suspend fun updateProgress(
        job: DownloadJob,
        progress: Progress,
    ) {
        dao.progress(job.id, progress.percent, progress.speed, progress.eta)
        latestProgress = progress
        showProgress(notifications.foreground(job, progress, queuedCount(dao.queue().first())))
    }

    private suspend fun showProgress(info: ForegroundInfo) {
        try {
            postProgress(info)
        } catch (failure: IllegalStateException) {
            throw PlatformProgressRefused(failure)
        } catch (failure: SecurityException) {
            throw PlatformProgressRefused(failure)
        }
    }

    private suspend fun merge(
        job: DownloadJob,
        mode: TrackMerge,
        inputs: List<File>,
        directory: File,
    ): File =
        try {
            if (dao.transition(job.id, JobState.Downloading, JobState.Merging) != 1) throw CancellationException()
            withContext(Dispatchers.IO) { requireStorage(context, directory, inputs.sumOf(File::length)) }
            muxTracks(mode, inputs, directory)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IOException) {
            throw DownloadFailure(storageError(failure, DownloadError.Merge), failure)
        } catch (failure: IllegalArgumentException) {
            throw DownloadFailure(DownloadError.Merge, failure)
        } catch (failure: IllegalStateException) {
            throw DownloadFailure(DownloadError.Merge, failure)
        }

    private suspend fun save(
        job: DownloadJob,
        media: File,
    ): SavedMedia =
        try {
            if (job.merge == null && job.type == MediaType.Video) verifyDirectVideo(media)
            val resolver = context.contentResolver
            val saved = reserveExport(resolver, media, job)
            dao.attachExport(job.id, saved.uri.toString(), saved.mime)
            publishMedia(resolver, media, job, saved)
            saved
        } catch (failure: DownloadFailure) {
            throw failure
        } catch (failure: IOException) {
            throw DownloadFailure(storageError(failure, DownloadError.Save), failure)
        } catch (failure: SecurityException) {
            throw DownloadFailure(DownloadError.Save, failure)
        }

    private suspend fun fail(
        job: DownloadJob,
        error: DownloadError,
    ): Boolean {
        val state = dao.observe(job.id).first()?.state
        if (state !in setOf(JobState.Downloading, JobState.Merging, JobState.Publishing)) return false
        val outcome = recoveryOutcome(error, context.hasConnection(), job.retries)
        dao.record(job.id, requireNotNull(state), outcome.state, outcome.error, outcome.retries)
        return outcome.retry
    }
}

private fun storageError(
    failure: IOException,
    fallback: DownloadError,
): DownloadError {
    val error = classifyDownloadError(failure.message.orEmpty())
    return if (error == DownloadError.NoSpace) error else fallback
}

internal fun queuedCount(jobs: List<DownloadJob>): Int = jobs.count { it.state == JobState.Queued }

private fun IOException.downloadError(): DownloadError =
    when (this) {
        is DownloadFailure -> error
        is EOFException, is SocketException, is SocketTimeoutException -> DownloadError.Transient
        else -> classifyDownloadError(message.orEmpty())
    }

private class PlatformProgressRefused(
    cause: RuntimeException,
) : RuntimeException(cause)

private val UserActions = setOf(JobState.Cancelling, JobState.Pausing)
