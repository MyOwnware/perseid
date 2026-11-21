package app.myownware.perseid.feature.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.myownware.perseid.core.engine.MediaPreview
import app.myownware.perseid.core.engine.PreviewEngine
import app.myownware.perseid.core.engine.PreviewError
import app.myownware.perseid.core.engine.PreviewFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal data class DownloadUiState(
    val link: String = "",
    val preview: PreviewState = PreviewState.Empty,
    val shareRevision: Int = 0,
)

internal sealed interface PreviewState {
    data object Empty : PreviewState

    data object Invalid : PreviewState

    data object Preparing : PreviewState

    data object Loading : PreviewState

    data class Ready(
        val media: MediaPreview,
    ) : PreviewState

    data class Failed(
        val error: PreviewError,
    ) : PreviewState
}

internal class DownloadViewModel(
    private val engine: PreviewEngine,
    private val scope: CoroutineScope? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DownloadUiState())
    val state = mutableState.asStateFlow()

    private var request: Job? = null
    private var prepared = false

    fun editLink(link: String) {
        if (link == state.value.link) return
        request?.cancel()
        mutableState.value = state.value.copy(link = link, preview = PreviewState.Empty)
    }

    fun paste(text: String) {
        editLink(firstSharedLink(text) ?: text)
        requestPreview()
    }

    fun share(text: String) {
        mutableState.value = state.value.copy(shareRevision = state.value.shareRevision + 1)
        paste(text)
    }

    fun cancelPreview() {
        if (request?.isActive != true) return
        request?.cancel()
        publish(PreviewState.Empty)
    }

    fun requestPreview() {
        request?.cancel()
        val url = validatedLink(state.value.link)
        if (url == null) {
            publish(PreviewState.Invalid)
            return
        }
        request = (scope ?: viewModelScope).launch { load(url) }
    }

    private suspend fun load(url: String) {
        try {
            val preview = withTimeout(PREVIEW_TIMEOUT_MILLIS) { fetch(url) }
            publish(PreviewState.Ready(preview))
        } catch (_: TimeoutCancellationException) {
            publish(PreviewState.Failed(PreviewError.Unknown))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: PreviewFailure) {
            publish(PreviewState.Failed(failure.error))
        } catch (_: Exception) {
            publish(PreviewState.Failed(PreviewError.Unknown))
        }
    }

    private suspend fun fetch(url: String): MediaPreview {
        if (!prepared) {
            publish(PreviewState.Preparing)
            engine.prepare()
            prepared = true
        }
        publish(PreviewState.Loading)
        return engine.preview(url)
    }

    private fun publish(preview: PreviewState) {
        mutableState.value = state.value.copy(preview = preview)
    }

    private companion object {
        const val PREVIEW_TIMEOUT_MILLIS = 30_000L
    }
}
