package app.myownware.perseid.core.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal data class QueueOutcome(
    val state: JobState,
    val retries: Int,
    val error: DownloadError? = null,
) {
    val retry get() = state == JobState.Waiting || state == JobState.Retrying
}

internal fun recoveryOutcome(
    error: DownloadError,
    connected: Boolean,
    retries: Int,
): QueueOutcome =
    when {
        error == DownloadError.CannotResume -> QueueOutcome(JobState.Failed, retries, error)
        !connected && error in NetworkErrors -> QueueOutcome(JobState.Waiting, retries)
        error in NetworkErrors && retries < MAX_RETRIES -> QueueOutcome(JobState.Retrying, retries + 1)
        else -> QueueOutcome(JobState.Failed, retries, error)
    }

internal val RecoverableStates =
    setOf(JobState.Queued, JobState.Downloading, JobState.Publishing, JobState.Waiting, JobState.Retrying)

internal fun Context.hasConnection(): Boolean {
    val manager = getSystemService(ConnectivityManager::class.java)
    val network = manager.activeNetwork ?: return false
    return manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}

private val NetworkErrors = setOf(DownloadError.Offline, DownloadError.Transient, DownloadError.Expired)
private const val MAX_RETRIES = 3
