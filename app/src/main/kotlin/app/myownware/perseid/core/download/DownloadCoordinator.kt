package app.myownware.perseid.core.download

import android.content.Context
import android.graphics.Bitmap
import androidx.room.withTransaction
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.myownware.perseid.core.engine.DownloadRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ExecutionException

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

    suspend fun cancel(id: String) {
        if (dao.transition(id, JobState.Queued, JobState.Cancelled) == 0) dao.cancel(id)
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
        try {
            if (image != null) writeThumbnail(image, thumbnail)
            val work =
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setId(id)
                    .setInputData(workDataOf(DOWNLOAD_ID to id.toString()))
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(DOWNLOAD_QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
                .result
                .get()
        } catch (failure: ExecutionException) {
            val error = classifyDownloadError(failure.message.orEmpty())
            dao.finish(job.id, JobState.Failed, error, System.currentTimeMillis())
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
