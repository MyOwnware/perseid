package app.myownware.perseid.core.download

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import app.myownware.perseid.PerseidApp

internal class DownloadWorker(
    private val context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        try {
            runDownloads()
        } finally {
            if (usesTransferFallback) notifications.cancel(FALLBACK_NOTIFICATION_TAG, TRANSFER_NOTIFICATION_ID)
        }

    private suspend fun runDownloads(): Result {
        val container = (context.applicationContext as PerseidApp).container
        val runner = DownloadRunner(context, container, ::showProgress)
        val retry =
            runner.run({
                val constrained =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        stopReason == WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY
                val offline = constrained || !context.hasConnection()
                container.downloads.interrupted(offline)
            })
        return if (retry) Result.retry() else Result.success()
    }

    private val usesTransferFallback get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    private val notifications get() = context.getSystemService(NotificationManager::class.java)

    private suspend fun showProgress(info: ForegroundInfo) {
        // A rejected transfer can start after the app has lost foreground-service eligibility.
        if (usesTransferFallback) {
            notifications.notify(FALLBACK_NOTIFICATION_TAG, info.notificationId, info.notification)
        } else {
            setForeground(info)
        }
    }
}

private const val FALLBACK_NOTIFICATION_TAG = "download-worker"
