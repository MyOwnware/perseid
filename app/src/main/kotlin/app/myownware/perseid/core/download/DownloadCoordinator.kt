package app.myownware.perseid.core.download

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.room.withTransaction
import app.myownware.perseid.core.engine.DownloadRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal class DownloadCoordinator(
    private val context: Context,
    private val database: DownloadDatabase,
) {
    private val dao = database.downloads()
    private val notifications = DownloadNotifications(context)
    private val scheduler = downloadScheduler(context)
    private val scheduling = Mutex()
    internal val execution = Mutex()
    val queue = dao.queue()

    suspend fun start(
        request: DownloadRequest,
        thumbnail: Bitmap?,
    ) {
        withContext(NonCancellable + Dispatchers.IO) {
            scheduling.withLock {
                enqueue(request, thumbnail)
                schedule()
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
            scheduling.withLock {
                val job = dao.observe(id).first() ?: return@withLock
                if (job.requiresRestart != restart || dao.requeue(id) != 1) return@withLock
                try {
                    if (restart) preserveAttempt(job)
                    schedule()
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
        withContext(NonCancellable + Dispatchers.IO) {
            scheduling.withLock {
                val pending = scheduler.pending()
                if (!pending && execution.tryLock()) {
                    try {
                        settleInterruptedActions(context, dao)
                    } finally {
                        execution.unlock()
                    }
                }
                val recovered = dao.transitionQueue(setOf(JobState.AndroidPaused), JobState.Queued)
                if (recovered > 0) notifications.clearAndroidPause()
                if ((!pending || recovered > 0) && queue.first().any { it.state in RecoverableStates }) schedule()
            }
        }
    }

    suspend fun next(
        jobId: Int?,
        finished: () -> Unit,
    ): DownloadJob? =
        scheduling.withLock {
            val job = queue.first().firstOrNull { it.state in RecoverableStates }
            if (job == null) {
                scheduler.finishing(jobId)
                finished()
            }
            job
        }

    suspend fun interrupted(
        lostConnection: Boolean = false,
        jobId: Int? = null,
    ) {
        scheduling.withLock {
            if (lostConnection) {
                dao.transitionQueue(setOf(JobState.Downloading, JobState.Merging), JobState.Waiting)
            } else {
                scheduler.finishing(jobId)
                pauseForAndroid(dao, notifications)
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
        if (image != null) writeThumbnail(image, thumbnail)
        dao.insert(job)
    }

    private suspend fun schedule() {
        if (!scheduler.schedule()) pauseForAndroid(dao, notifications)
    }
}

private suspend fun pauseForAndroid(
    dao: DownloadDao,
    notifications: DownloadNotifications,
) {
    if (dao.transitionQueue(RecoverableStates, JobState.AndroidPaused) > 0) notifications.androidPaused()
}

private fun DownloadScheduler.finishing(jobId: Int?) {
    if (jobId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        (this as DataTransferScheduler).finishing(jobId)
    }
}

internal suspend fun settleInterruptedActions(
    context: Context,
    dao: DownloadDao,
) {
    for (job in dao.queue().first()) {
        if (job.state == JobState.Pausing || job.state == JobState.Cancelling) settleUserAction(context, dao, job.id)
    }
}

internal suspend fun settleUserAction(
    context: Context,
    dao: DownloadDao,
    id: String,
) {
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
            return
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

private const val PNG_QUALITY = 100

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
