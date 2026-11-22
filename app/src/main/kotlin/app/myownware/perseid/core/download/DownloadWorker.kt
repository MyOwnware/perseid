package app.myownware.perseid.core.download

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import app.myownware.perseid.PerseidApp

internal class DownloadWorker(
    private val context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return Result.success()
        val container = (context.applicationContext as PerseidApp).container
        val runner = DownloadRunner(context, container, ::setForeground)
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
}
