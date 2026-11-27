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
import app.myownware.perseid.core.preferences.Preferences
import app.myownware.perseid.core.preferences.VideoQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
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
                ready.media.author,
                ready.media.durationSeconds,
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
    private val preferences: () -> Preferences = { Preferences() },
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
        if (link != state.value.link) updateLink(link, TYPING_DEBOUNCE_MILLIS)
    }

    fun paste(text: String) {
        updateLink(firstSharedLink(text) ?: text, 0)
    }

    fun share(text: String?) {
        mutableState.update { it.copy(shareRevision = it.shareRevision + 1) }
        if (text != null) paste(text)
    }

    private fun updateLink(
        link: String,
        debounceMillis: Long,
    ) {
        request?.cancel()
        mutableState.update { it.copy(link = link, preview = PreviewState.Empty) }
        if (link.isBlank()) return
        request =
            (scope ?: viewModelScope).launch {
                delay(debounceMillis)
                val url = validatedLink(link)
                if (url == null) publish(PreviewState.Invalid) else load(url)
            }
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

    private suspend fun load(url: String) {
        try {
            val preview = withTimeout(PREVIEW_TIMEOUT_MILLIS) { fetch(url) }
            publish(initialPreview(preview, url, preferences()))
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
        const val TYPING_DEBOUNCE_MILLIS = 250L
        const val PREVIEW_TIMEOUT_MILLIS = 30_000L
    }
}

internal fun initialPreview(
    media: MediaPreview,
    url: String,
    defaults: Preferences,
): PreviewState.Ready {
    val index = defaultVideoIndex(media.options.video, defaults.quality)
    val audio = media.options.audio != null
    val type =
        when {
            defaults.choice == MediaType.Audio && audio -> MediaType.Audio
            index < 0 && audio -> MediaType.Audio
            else -> MediaType.Video
        }
    return PreviewState.Ready(media, url, index, type)
}

internal fun defaultVideoIndex(
    options: List<MediaOption>,
    quality: VideoQuality = VideoQuality.UpTo1080,
): Int {
    if (quality == VideoQuality.Best) return if (options.isEmpty()) -1 else 0
    val limit = checkNotNull(quality.heightLimit)
    val known = options.indices.filter { options[it].height != null }
    val withinLimit = known.filter { checkNotNull(options[it].height) <= limit }
    return withinLimit.maxByOrNull { checkNotNull(options[it].height) }
        ?: known.minByOrNull { checkNotNull(options[it].height) }
        ?: options.lastIndex
}
