package app.myownware.perseid.core.download

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

internal interface DownloadScheduler {
    fun pending(): Boolean

    fun schedule(): Boolean
}

internal fun downloadScheduler(context: Context): DownloadScheduler =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        DataTransferScheduler(context)
    } else {
        WorkDownloadScheduler(context)
    }

private class WorkDownloadScheduler(
    context: Context,
) : DownloadScheduler {
    private val manager = WorkManager.getInstance(context)

    override fun pending(): Boolean = manager.getWorkInfosForUniqueWork(QUEUE).get().any { !it.state.isFinished }

    override fun schedule(): Boolean {
        val request =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        return try {
            manager.enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request).result.get()
            true
        } catch (_: ExecutionException) {
            false
        }
    }
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class DataTransferScheduler(
    private val context: Context,
) : DownloadScheduler {
    private val manager = context.getSystemService(JobScheduler::class.java)
    private var finishedId: Int? = null

    override fun pending(): Boolean = JOB_IDS.any { it != finishedId && manager.getPendingJob(it) != null }

    override fun schedule(): Boolean {
        if (pending()) return true
        val id = JOB_IDS.first { it != finishedId }
        val job =
            JobInfo
                .Builder(id, ComponentName(context, DownloadJobService::class.java))
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build()
        return try {
            manager.schedule(job) == JobScheduler.RESULT_SUCCESS
        } catch (_: IllegalStateException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun finishing(id: Int) {
        // JobScheduler can still report the completed job while it processes jobFinished.
        finishedId = id
    }
}

private const val QUEUE = "perseid.downloads"
private const val BACKOFF_SECONDS = 10L
internal const val FIRST_TRANSFER_JOB_ID = 10_000
private val JOB_IDS = FIRST_TRANSFER_JOB_ID..FIRST_TRANSFER_JOB_ID + 1
