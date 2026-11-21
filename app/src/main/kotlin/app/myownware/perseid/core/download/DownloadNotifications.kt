package app.myownware.perseid.core.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import app.myownware.perseid.MainActivity
import app.myownware.perseid.PerseidApp
import app.myownware.perseid.R
import app.myownware.perseid.core.locale.TransferMetrics
import app.myownware.perseid.core.locale.formatPercent
import app.myownware.perseid.core.locale.formatTransferDetails
import app.myownware.perseid.core.locale.localizedContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal const val SHOW_DOWNLOAD = "app.myownware.perseid.SHOW_DOWNLOAD"
private const val CHANNEL_ID = "downloads"

internal class DownloadNotifications(
    private val context: Context,
) {
    fun foreground(
        job: DownloadJob,
        progress: Progress? = null,
    ): ForegroundInfo {
        val local = localizedContext(context)
        val text = progress?.let { progressText(local, it) } ?: local.getString(R.string.download_running)
        val notification =
            builder(local, job.title)
                .setContentText(text)
                .setProgress(MAX_PERCENT, progress?.percent ?: 0, progress == null)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(0, local.getString(R.string.download_cancel_action), cancelIntent(job.id))
                .build()
        return ForegroundInfo(job.id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    fun saved(
        job: DownloadJob,
        media: SavedMedia,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val local = localizedContext(context)
        val notification =
            builder(local, job.title)
                .setContentText(local.getString(R.string.download_saved))
                .setAutoCancel(true)
                .addAction(0, local.getString(R.string.download_open), activity(media.openIntent(), 1))
                .addAction(
                    0,
                    local.getString(R.string.download_share),
                    activity(media.shareIntent(), job.id.hashCode()),
                ).build()
        context.getSystemService(NotificationManager::class.java).notify(job.id, 2, notification)
    }

    private fun builder(
        local: Context,
        title: String,
    ): NotificationCompat.Builder {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                local.getString(R.string.settings_downloads),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val open = Intent(context, MainActivity::class.java).setAction(SHOW_DOWNLOAD)
        return NotificationCompat
            .Builder(local, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentIntent(activity(open, 0))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    }

    private fun activity(
        intent: Intent,
        code: Int,
    ): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(context, code, intent, flags)
    }

    private fun cancelIntent(id: String): PendingIntent {
        val intent =
            Intent(context, CancelDownloadReceiver::class.java).setData(Uri.fromParts("perseid", id, null))
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }
}

private fun progressText(
    context: Context,
    progress: Progress,
): String {
    val locale = context.resources.configuration.locales[0]
    val percentage = formatPercent(progress.percent.toFloat() / MAX_PERCENT, locale)
    val (_, speed, eta) = progress
    if (speed == null || eta == null) return percentage
    val details = formatTransferDetails(context, TransferMetrics(speed, eta))
    return context.getString(R.string.download_progress_details, percentage, details)
}

internal class CancelDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val id = intent.data?.schemeSpecificPart ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as PerseidApp).container.downloads.cancel(id)
            } finally {
                pending.finish()
            }
        }
    }
}

private fun SavedMedia.openIntent(): Intent =
    Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

private fun SavedMedia.shareIntent(): Intent {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    return Intent.createChooser(send, null)
}

internal fun launchSavedMedia(
    context: Context,
    media: SavedMedia,
    share: Boolean,
): Boolean =
    try {
        context.startActivity(if (share) media.shareIntent() else media.openIntent())
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
