package app.myownware.perseid.core.engine

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.net.URI
import java.util.UUID

internal class YtDlpPreviewEngine(
    private val context: Context,
) : PreviewEngine {
    override suspend fun prepare() {
        runInterruptible(Dispatchers.IO) { BundledEngine.prepare(context) }
    }

    override suspend fun preview(url: String): MediaPreview {
        val metadata = runInterruptible(Dispatchers.IO) { readMetadata(extractMetadata(url)) }
        val thumbnailUrl = metadata.metadataText("thumbnail")
        return MediaPreview(
            title = metadata.metadataText("title") ?: throw PreviewFailure(PreviewError.Unknown),
            author = metadata.metadataText("uploader") ?: metadata.metadataText("channel"),
            durationSeconds = metadata.path("duration").asLong(-1).takeIf { it >= 0 },
            thumbnail = thumbnailUrl?.let { loadThumbnail(it) },
            thumbnailUrl = thumbnailUrl,
            options =
                if (metadata.metadataText("extractor_key") == "Generic" && URI.create(url).rawQuery != null) {
                    MediaOptions()
                } else {
                    metadata.mediaOptions()
                },
        )
    }

    private fun extractMetadata(url: String): String {
        val request =
            YoutubeDLRequest(url).apply {
                addOption("--ignore-config")
                addOption("--no-plugin-dirs")
                addOption("--no-remote-components")
                addOption("--no-update")
                addOption("--no-playlist")
                addOption("--skip-download")
                addOption("--no-check-formats")
                addOption("--dump-single-json")
                addOption("--socket-timeout", SOCKET_TIMEOUT_SECONDS)
                addOption("--retries", 0)
                addOption("--extractor-retries", 0)
            }
        val response =
            try {
                BundledEngine.execute(request, UUID.randomUUID().toString())
            } catch (failure: YoutubeDLException) {
                val error = classifyPreviewError(failure.message.orEmpty(), URI.create(url).host)
                throw PreviewFailure(error, failure)
            }
        return response.out
    }

    private companion object {
        const val SOCKET_TIMEOUT_SECONDS = 12
    }
}
