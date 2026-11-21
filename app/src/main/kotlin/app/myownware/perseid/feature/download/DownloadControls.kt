package app.myownware.perseid.feature.download

import app.myownware.perseid.core.download.DownloadCoordinator
import app.myownware.perseid.core.download.DownloadJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class DownloadControls(
    private val downloads: DownloadCoordinator?,
    private val scope: () -> CoroutineScope,
) {
    private var cancelTimer: Job? = null
    private val pendingCancel = MutableStateFlow<String?>(null)
    val pending = pendingCancel.asStateFlow()

    fun start(state: DownloadUiState) {
        val request = state.downloadRequest() ?: return
        val thumbnail = (state.preview as? PreviewState.Ready)?.media?.thumbnail
        scope().launch { downloads?.start(request, thumbnail) }
    }

    fun pause(job: DownloadJob) {
        scope().launch { downloads?.pause(job.id) }
    }

    fun resume(job: DownloadJob) {
        scope().launch { downloads?.resume(job.id) }
    }

    fun requestCancel(job: DownloadJob) {
        if (!job.canCancel || pending.value != null) return
        pendingCancel.value = job.id
        cancelTimer =
            scope().launch {
                delay(CANCEL_UNDO_MILLIS)
                downloads?.cancel(job.id)
                pendingCancel.value = null
            }
    }

    fun undoCancel() {
        cancelTimer?.cancel()
        pendingCancel.value = null
    }
}

private const val CANCEL_UNDO_MILLIS = 5_000L
