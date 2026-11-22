package app.myownware.perseid.core.download

import android.content.Context
import android.graphics.Bitmap
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.myownware.perseid.core.engine.DownloadRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

internal class DownloadCoordinator(
    private val context: Context,
    private val database: DownloadDatabase,
) {
    private val dao = database.downloads()
    val queue = dao.queue()

    suspend fun start(
        request: DownloadRequest,
        thumbnail: Bitmap?,
    ) {
        withContext(NonCancellable + Dispatchers.IO) {
            database.withTransaction {
                enqueue(request, thumbnail)
            }
        }
    }

    suspend fun pause(id: String) {
        changeState(id) { state ->
            when (state) {
                JobState.Downloading, JobState.Merging -> JobState.Pausing
                JobState.Waiting, JobState.Retrying -> JobState.Paused
                else -> null
            }
        }
    }

    suspend fun resume(
        id: String,
        restart: Boolean = false,
    ) {
        withContext(NonCancellable + Dispatchers.IO) {
            database.withTransaction {
                if (dao.requeue(id, restart) != 1) return@withTransaction
                try {
                    if (restart) preserveAttempt(requireNotNull(dao.observe(id).first()))
                    schedule(id)
                } catch (_: IOException) {
                    dao.record(id, JobState.Queued, JobState.Failed, DownloadError.CannotResume, 0)
                }
            }
        }
    }

    suspend fun cancel(id: String) {
        withContext(NonCancellable + Dispatchers.IO) {
            database.withTransaction {
                val job = dao.observe(id).first() ?: return@withTransaction
                if (!job.canCancel) return@withTransaction
                when (job.state) {
                    JobState.Downloading, JobState.Merging, JobState.Pausing -> {
                        dao.transition(id, job.state, JobState.Cancelling)
                    }

                    else -> {
                        deletePendingExport(context.contentResolver, job.contentUri)
                        File(job.stagingPath).deleteRecursively()
                        dao.transition(id, job.state, JobState.Cancelled)
                    }
                }
            }
        }
    }

    suspend fun reconcile() {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val manager = WorkManager.getInstance(context)
                val jobs = dao.queue().first()
                for (job in jobs) {
                    if (job.state == JobState.Saved || !manager.hasDownloadWork(job)) reconcileJob(job)
                }
            }
        }
    }

    private suspend fun reconcileJob(job: DownloadJob) {
        when (job.state) {
            JobState.Pausing -> {
                dao.transition(job.id, JobState.Pausing, JobState.Paused)
            }

            JobState.Cancelling -> {
                deletePendingExport(context.contentResolver, job.contentUri)
                File(job.stagingPath).deleteRecursively()
                dao.transition(job.id, JobState.Cancelling, JobState.Cancelled)
            }

            in RecoverableStates -> {
                schedule(job.id)
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun changeState(
        id: String,
        next: (JobState) -> JobState?,
    ) {
        database.withTransaction {
            val current = dao.observe(id).first()?.state ?: return@withTransaction
            next(current)?.let { dao.transition(id, current, it) }
        }
    }

    private suspend fun enqueue(
        request: DownloadRequest,
        thumbnail: Bitmap?,
    ) {
        val id = UUID.randomUUID()
        val staging = File(context.noBackupFilesDir, "transfers/$id")
        val image = thumbnail?.let { File(context.noBackupFilesDir, "download-thumbnails/$id.png") }
        val job = request.toJob(id, staging, image)
        dao.insert(job)
        if (image != null) writeThumbnail(image, thumbnail)
        schedule(job.id)
    }

    private suspend fun schedule(id: String) {
        val request =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .addTag(downloadTag(id))
                .setInputData(workDataOf(DOWNLOAD_ID to id))
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        try {
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(DOWNLOAD_QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
                .result
                .get()
        } catch (_: ExecutionException) {
            dao.record(id, JobState.Queued, JobState.Failed, DownloadError.Unknown, 0)
        }
    }
}

private fun DownloadRequest.toJob(
    id: UUID,
    staging: File,
    image: File?,
): DownloadJob =
    DownloadJob(
        id.toString(),
        url,
        type,
        formatIds.joinToString("\n"),
        title,
        image?.path,
        staging.path,
        merge,
        System.currentTimeMillis(),
    )

private fun writeThumbnail(
    file: File,
    thumbnail: Bitmap,
) {
    try {
        file.parentFile?.mkdirs()
        file.outputStream().use { thumbnail.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    } catch (_: IOException) {
        // A missing thumbnail can use the standard media placeholder.
    }
}

internal const val DOWNLOAD_ID = "download_id"
private const val DOWNLOAD_QUEUE = "perseid.downloads"
private const val PNG_QUALITY = 100

internal fun downloadTag(id: String): String = "download:$id"

private const val BACKOFF_SECONDS = 10L

private fun preserveAttempt(job: DownloadJob) {
    val staging = File(job.stagingPath)
    val files = staging.listFiles().orEmpty().filter(File::isFile)
    if (files.isEmpty()) return
    val archive = File(staging, "previous-${UUID.randomUUID()}")
    if (!archive.mkdir()) throw IOException()
    for (file in files) {
        Files.move(file.toPath(), File(archive, file.name).toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
}

private fun WorkManager.hasDownloadWork(job: DownloadJob): Boolean {
    if (job.state !in RecoverableStates && job.state !in setOf(JobState.Pausing, JobState.Cancelling)) return true
    return getWorkInfosByTag(downloadTag(job.id)).get().any { !it.state.isFinished } ||
        getWorkInfoById(UUID.fromString(job.id)).get()?.state?.isFinished == false
}
