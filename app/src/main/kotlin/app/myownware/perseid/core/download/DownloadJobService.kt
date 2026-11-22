package app.myownware.perseid.core.download

import android.app.Notification
import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import androidx.annotation.RequiresApi
import app.myownware.perseid.PerseidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class DownloadJobService : JobService() {
    private var transfer: RunningTransfer? = null

    override fun onStartJob(params: JobParameters): Boolean {
        showNotification(params, DownloadNotifications(this).starting())
        val container = (application as PerseidApp).container
        val coordinator = container.downloads
        val runner =
            DownloadRunner(this, container) {
                showNotification(params, it.notification)
            }
        val offline = AtomicBoolean()
        val work =
            CoroutineScope(Dispatchers.IO).launch(start = CoroutineStart.UNDISPATCHED) {
                val retry =
                    runner.run({ coordinator.interrupted(offline.get(), params.jobId) }, params.jobId) {
                        jobFinished(params, false)
                    }
                if (retry) jobFinished(params, true)
            }
        transfer = RunningTransfer(params.jobId, offline, work)
        return true
    }

    private fun showNotification(
        params: JobParameters,
        notification: Notification,
    ) {
        setNotification(params, TRANSFER_NOTIFICATION_ID, notification, JOB_END_NOTIFICATION_POLICY_REMOVE)
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val offline = params.stopReason == JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY
        val active = transfer
        if (active?.id == params.jobId) {
            active.offline.set(offline)
            active.work.cancel()
            transfer = null
        }
        return offline
    }
}

private data class RunningTransfer(
    val id: Int,
    val offline: AtomicBoolean,
    val work: Job,
)
