package app.myownware.perseid.feature.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.myownware.perseid.core.download.DownloadCoordinator
import app.myownware.perseid.core.download.DownloadJob
import app.myownware.perseid.core.engine.DownloadRequest
import app.myownware.perseid.core.engine.MediaOption
import app.myownware.perseid.core.engine.MediaPreview
import app.myownware.perseid.core.engine.MediaType
import app.myownware.perseid.core.engine.PreviewEngine
import app.myownware.perseid.core.engine.PreviewError
import app.myownware.perseid.core.engine.PreviewFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal data class DownloadUiState(
    val link: String = "",
    val preview: PreviewState = PreviewState.Empty,
    val shareRevision: Int = 0,
    val queue: List<DownloadJob> = emptyList(),
) {
    fun downloadRequest(): DownloadRequest? {
        val ready = preview as? PreviewState.Ready ?: return null
        return ready.selectedOption?.let { option ->
            DownloadRequest(
                ready.url,
                ready.type,
                option.formatIds,
                ready.media.title,
                ready.media.thumbnailUrl,
                option.merge,
            )
        }
    }
}

internal sealed interface PreviewState {
    data object Empty : PreviewState

    data object Invalid : PreviewState

    data object Preparing : PreviewState

    data object Loading : PreviewState

    data class Ready(
        val media: MediaPreview,
        val url: String,
        val videoIndex: Int = defaultVideoIndex(media.options.video),
        val type: MediaType = if (videoIndex < 0 && media.options.audio != null) MediaType.Audio else MediaType.Video,
    ) : PreviewState {
        val selectedOption: MediaOption?
            get() =
                when (type) {
                    MediaType.Video -> media.options.video.getOrNull(videoIndex)
                    MediaType.Audio -> media.options.audio
                }
    }

    data class Failed(
        val error: PreviewError,
    ) : PreviewState
}

internal class DownloadViewModel(
    private val engine: PreviewEngine,
    private val scope: CoroutineScope? = null,
    private val downloads: DownloadCoordinator? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DownloadUiState())
    val state = mutableState.asStateFlow()

    private var request: Job? = null
    private var prepared = false
    val controls = DownloadControls(downloads) { scope ?: viewModelScope }

    init {
        if (downloads != null) {
            (scope ?: viewModelScope).launch {
                downloads.queue.collect { jobs -> mutableState.update { it.copy(queue = jobs) } }
            }
        }
    }

    fun editLink(link: String) {
        if (link == state.value.link) return
        request?.cancel()
        mutableState.value = state.value.copy(link = link, preview = PreviewState.Empty)
    }

    fun paste(text: String) {
        editLink(firstSharedLink(text) ?: text)
        requestPreview()
    }

    fun share(text: String?) {
        mutableState.update { it.copy(shareRevision = it.shareRevision + 1) }
        if (text != null) paste(text)
    }

    fun cancelPreview() {
        if (request?.isActive != true) return
        request?.cancel()
        publish(PreviewState.Empty)
    }

    fun selectType(type: MediaType) {
        val ready = state.value.preview as? PreviewState.Ready ?: return
        val options = ready.media.options
        val available =
            when (type) {
                MediaType.Video -> options.video.isNotEmpty()
                MediaType.Audio -> options.audio != null
            }
        if (available) publish(ready.copy(type = type))
    }

    fun selectVideo(index: Int) {
        val ready = state.value.preview as? PreviewState.Ready ?: return
        if (ready.type == MediaType.Video && index in ready.media.options.video.indices) {
            publish(ready.copy(videoIndex = index))
        }
    }

    fun requestPreview() {
        request?.cancel()
        val url = validatedLink(state.value.link)
        if (url == null) {
            publish(PreviewState.Invalid)
            return
        }
        publish(PreviewState.Loading)
        request = (scope ?: viewModelScope).launch { load(url) }
    }

    private suspend fun load(url: String) {
        try {
            val preview = withTimeout(PREVIEW_TIMEOUT_MILLIS) { fetch(url) }
            publish(PreviewState.Ready(preview, url))
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

internal const val DEFAULT_VIDEO_HEIGHT = 1080

private fun defaultVideoIndex(options: List<MediaOption>): Int {
    val lowestKnown = options.indexOfLast { it.height != null }
    val withinLimit = options.indexOfFirst { it.height != null && it.height <= DEFAULT_VIDEO_HEIGHT }
    return when {
        withinLimit >= 0 -> withinLimit
        lowestKnown >= 0 -> lowestKnown
        else -> options.lastIndex
    }
}
