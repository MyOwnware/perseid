package app.myownware.perseid.core.download

import android.content.Context
import android.database.SQLException
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.myownware.perseid.PerseidApp
import app.myownware.perseid.core.engine.MediaType
import app.myownware.perseid.core.engine.TrackMerge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException

internal class DownloadWorker(
    private val context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    private val container get() = (context.applicationContext as PerseidApp).container
    private val dao get() = container.downloadDao
    private val notifications = DownloadNotifications(context)

    override suspend fun doWork(): Result {
        val job = inputData.getString(DOWNLOAD_ID)?.let { dao.observe(it).first() } ?: return Result.success()
        return try {
            runJob(job)
        } catch (cancelled: CancellationException) {
            recordInterrupted(job)
            throw cancelled
        } catch (failure: IOException) {
            fail(job, failure.downloadError())
        } catch (_: IllegalStateException) {
            fail(job, DownloadError.Unknown)
        } catch (_: SecurityException) {
            fail(job, DownloadError.Unknown)
        } catch (_: SQLException) {
            fail(job, DownloadError.Save)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { settleUserAction(job.id) }
        }
    }

    private suspend fun runJob(job: DownloadJob): Result {
        if (job.state !in RecoverableStates || dao.transition(job.id, job.state, JobState.Downloading) != 1) {
            return Result.success()
        }
        setForeground(notifications.foreground(job, queued = queuedCount(dao.queue().first())))
        withContext(Dispatchers.IO) { requireStorage(context, context.noBackupFilesDir, 1) }
        download(job)
        return Result.success()
    }

    private suspend fun recordInterrupted(job: DownloadJob) {
        withContext(NonCancellable) {
            val next = if (context.hasConnection()) JobState.Queued else JobState.Waiting
            if (dao.transition(job.id, JobState.Downloading, next) == 0) {
                dao.transition(job.id, JobState.Merging, next)
            }
        }
    }

    private suspend fun download(job: DownloadJob) {
        val media = transferUntilCancelled(job) ?: return
        if (dao.beginPublishing(job.id) != 1) return
        val saved = save(job, media)
        withContext(NonCancellable) {
            dao.saved(job.id, saved.uri.toString(), saved.mime, System.currentTimeMillis())
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
                        setForeground(notifications.foreground(job, latestProgress, count))
                    }
                }
            val transfer =
                async {
                    transferTracks(container.transferEngine, job, { progress.trySend(it) }) { mode, tracks, folder ->
                        merge(job, mode, tracks, folder)
                    }
                }
            val stop = setOf(JobState.Cancelling, JobState.Pausing)
            val cancellation = async { dao.observe(job.id).first { it?.state in stop } }
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
        setForeground(notifications.foreground(job, progress, queuedCount(dao.queue().first())))
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
    ): Result {
        val state = dao.observe(job.id).first()?.state
        if (state !in setOf(JobState.Downloading, JobState.Merging, JobState.Publishing)) return Result.success()
        val outcome = recoveryOutcome(error, context.hasConnection(), job.retries)
        dao.record(job.id, requireNotNull(state), outcome.state, outcome.error, outcome.retries)
        return if (outcome.retry) Result.retry() else Result.success()
    }

    private suspend fun settleUserAction(id: String) {
        val job = dao.observe(id).first() ?: return
        when (job.state) {
            JobState.Pausing -> {
                dao.transition(id, JobState.Pausing, JobState.Paused)
            }

            JobState.Cancelling -> {
                deletePendingExport(context.contentResolver, job.contentUri)
                File(job.stagingPath).deleteRecursively()
                dao.transition(id, JobState.Cancelling, JobState.Cancelled)
            }

            else -> {
                Unit
            }
        }
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
