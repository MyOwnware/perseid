package app.myownware.perseid.core.download

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

internal interface DownloadScheduler {
    fun pending(): Boolean

    fun schedule(): Boolean

    fun finishing(id: Int) {}
}

internal fun downloadScheduler(context: Context): DownloadScheduler =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        FallbackDownloadScheduler(DataTransferScheduler(context), WorkDownloadScheduler(context))
    } else {
        WorkDownloadScheduler(context)
    }

internal class FallbackDownloadScheduler(
    private val transfer: DownloadScheduler,
    private val work: DownloadScheduler,
) : DownloadScheduler {
    override fun pending(): Boolean = transfer.pending() || work.pending()

    override fun schedule(): Boolean = (!work.pending() && transfer.schedule()) || work.schedule()

    override fun finishing(id: Int) = transfer.finishing(id)
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
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }
                }.build()
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
            val accepted = manager.schedule(job) == JobScheduler.RESULT_SUCCESS
            if (!accepted) Log.w(DOWNLOAD_TAG, "JobScheduler rejected the transfer job")
            accepted
        } catch (failure: IllegalStateException) {
            Log.w(DOWNLOAD_TAG, "JobScheduler could not schedule the transfer job", failure)
            false
        } catch (failure: SecurityException) {
            Log.w(DOWNLOAD_TAG, "JobScheduler refused the transfer job", failure)
            false
        }
    }

    override fun finishing(id: Int) {
        // JobScheduler can still report the completed job while it processes jobFinished.
        finishedId = id
    }
}

internal const val DOWNLOAD_TAG = "PerseidDownload"
private const val QUEUE = "perseid.downloads"
private const val BACKOFF_SECONDS = 10L
internal const val FIRST_TRANSFER_JOB_ID = 10_000
private val JOB_IDS = FIRST_TRANSFER_JOB_ID..FIRST_TRANSFER_JOB_ID + 1
