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
import java.io.File
import java.io.IOException

internal class DownloadWorker(
    private val context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    private val container get() = (context.applicationContext as PerseidApp).container
    private val dao get() = container.downloadDao
    private val notifications = DownloadNotifications(context)

    override suspend fun doWork(): Result {
        val id = inputData.getString(DOWNLOAD_ID) ?: return Result.failure()
        return try {
            if (dao.transition(id, JobState.Queued, JobState.Downloading) == 1) {
                val job = requireNotNull(dao.observe(id).first())
                setForeground(notifications.foreground(job, queued = queuedCount(dao.queue().first())))
                download(job)
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            conclude(id, JobState.Waiting, null)
            throw cancelled
        } catch (failure: IOException) {
            fail(id, (failure as? DownloadFailure)?.error ?: classifyDownloadError(failure.message.orEmpty()))
        } catch (_: IllegalStateException) {
            fail(id, DownloadError.Unknown)
        } catch (_: SecurityException) {
            fail(id, DownloadError.Unknown)
        } catch (_: SQLException) {
            fail(id, DownloadError.Save)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { cleanCancelled(id) }
        }
    }

    private suspend fun download(job: DownloadJob) {
        val media = transferUntilCancelled(job) ?: return
        if (dao.transition(job.id, JobState.Downloading, JobState.Publishing) != 1) return
        withContext(NonCancellable) {
            val saved = save(job, media)
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
            val transfer = async { transferTracks(container.transferEngine, job, { progress.trySend(it) }, ::merge) }
            val cancellation = async { dao.observe(job.id).first { it?.state == JobState.Cancelling } }
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
        mode: TrackMerge,
        inputs: List<File>,
        directory: File,
    ): File =
        try {
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
            publishMedia(context.contentResolver, media, job)
        } catch (failure: DownloadFailure) {
            throw failure
        } catch (failure: IOException) {
            throw DownloadFailure(storageError(failure, DownloadError.Save), failure)
        } catch (failure: SecurityException) {
            throw DownloadFailure(DownloadError.Save, failure)
        }

    private suspend fun fail(
        id: String,
        error: DownloadError,
    ): Result {
        conclude(id, JobState.Failed, error)
        return Result.success()
    }

    private suspend fun conclude(
        id: String,
        state: JobState,
        error: DownloadError?,
    ) {
        withContext(NonCancellable) {
            dao.finish(id, state, error, System.currentTimeMillis().takeUnless { state == JobState.Waiting })
        }
    }

    private suspend fun cleanCancelled(id: String) {
        val job = dao.observe(id).first()?.takeIf { it.state == JobState.Cancelling } ?: return
        File(job.stagingPath).deleteRecursively()
        dao.finish(id, JobState.Cancelled, null, System.currentTimeMillis())
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
