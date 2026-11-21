package app.myownware.perseid.core.engine

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject
import java.util.UUID

internal class YtDlpPreviewEngine(
    private val context: Context,
) : PreviewEngine {
    override suspend fun prepare() {
        runInterruptible(Dispatchers.IO) { YoutubeDL.init(context) }
    }

    override suspend fun preview(url: String): MediaPreview {
        val metadata = runInterruptible(Dispatchers.IO) { extractMetadata(url) }
        return MediaPreview(
            title = metadata.nonEmpty("title") ?: throw PreviewFailure(PreviewError.Unknown),
            author = metadata.nonEmpty("uploader") ?: metadata.nonEmpty("channel"),
            durationSeconds = metadata.optLong("duration", -1).takeIf { it >= 0 },
            thumbnail = metadata.nonEmpty("thumbnail")?.let { loadThumbnail(it) },
        )
    }

    private fun extractMetadata(url: String): JSONObject {
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
                YoutubeDL.execute(request, UUID.randomUUID().toString())
            } catch (failure: YoutubeDLException) {
                throw PreviewFailure(classifyPreviewError(failure.message.orEmpty()), failure)
            }
        return JSONObject(response.out)
    }

    private fun JSONObject.nonEmpty(key: String): String? = optString(key).takeUnless { isNull(key) || it.isBlank() }

    private companion object {
        const val SOCKET_TIMEOUT_SECONDS = 12
    }
}
