package app.myownware.perseid.core.engine

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.URI

private val YoutubePreviewClients = listOf("visionos", "default")
internal const val YOUTUBE_PREVIEW_EXTRA_MILLIS = 8_000L
private const val MAX_PREVIEW_HEIGHT = 1080

internal fun isYoutubePreview(url: String): Boolean {
    val host = URI.create(url).host?.lowercase() ?: return false
    return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
}

internal suspend fun youtubePreviewMetadata(extract: suspend (String) -> JsonNode): Pair<JsonNode, String> {
    val preferred = YoutubePreviewClients.first()
    val metadata =
        withTimeoutOrNull(YOUTUBE_PREVIEW_EXTRA_MILLIS) {
            try {
                extract(preferred).takeIf { it.metadataText("title") != null && it.hasPlayableYoutubeVideo() }
            } catch (_: PreviewFailure) {
                null
            } catch (_: IOException) {
                null
            }
        }
    if (metadata != null) return metadata to preferred
    val fallback = YoutubePreviewClients.last()
    return extract(fallback) to fallback
}

private fun JsonNode.hasPlayableYoutubeVideo(): Boolean {
    val streams = path("formats").mapNotNull { it.toStreamFormat() }
    val hasAudio = streams.any { it.isAudioOnly && it.isAac && it.isMp4Audio }
    return streams.any { stream ->
        stream.height?.let { it in 1..MAX_PREVIEW_HEIGHT } == true &&
            stream.extension == "mp4" && stream.isH264 &&
            ((stream.isCombined && stream.isAac) || (stream.isVideoOnly && hasAudio))
    }
}
