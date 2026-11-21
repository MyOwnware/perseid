package app.myownware.perseid.core.download

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.IOException
import java.util.Locale

internal data class Progress(
    val percent: Int,
    val speed: Long?,
    val eta: Long?,
)

internal fun interface TransferEngine {
    suspend fun fetch(
        job: DownloadJob,
        format: String,
        index: Int,
        progress: (Progress) -> Unit,
    ): File
}

internal class DownloadFailure(
    val error: DownloadError,
    cause: Throwable? = null,
) : IOException(cause)

internal class YtDlpTransferEngine(
    private val context: Context,
) : TransferEngine {
    override suspend fun fetch(
        job: DownloadJob,
        format: String,
        index: Int,
        progress: (Progress) -> Unit,
    ): File =
        runInterruptible(Dispatchers.IO) {
            val directory = File(job.stagingPath)
            directory
                .listFiles()
                .orEmpty()
                .singleOrNull { isTrack(it, index) }
                ?.let { return@runInterruptible it }
            if (directory.listFiles().orEmpty().any { it.name.startsWith("track-$index.") && it.length() > 0 }) {
                throw DownloadFailure(DownloadError.CannotResume)
            }
            val request = transferRequest(job.url, format, File(directory, "track-$index.%(ext)s"))
            try {
                YoutubeDL.init(context)
                YoutubeDL.execute(request, "${job.id}-$index") { _, _, line ->
                    parseProgress(line)?.let(progress)
                }
            } catch (error: YoutubeDLException) {
                throw DownloadFailure(classifyDownloadError(error.message.orEmpty()), error)
            }
            directory.listFiles().orEmpty().singleOrNull { isTrack(it, index) }
                ?: throw DownloadFailure(DownloadError.Refused)
        }
}

private fun isTrack(
    file: File,
    index: Int,
): Boolean =
    file.isFile && file.length() > 0 && file.nameWithoutExtension == "track-$index" &&
        file.extension in MediaExtensions

private fun transferRequest(
    url: String,
    format: String,
    output: File,
): YoutubeDLRequest =
    YoutubeDLRequest(url).apply {
        for (flag in TransferFlags) addOption(flag)
        addOption("--format", formatSelector(format))
        addOption("--output", output.absolutePath)
        addOption("--socket-timeout", SOCKET_TIMEOUT_SECONDS)
        addOption("--retries", 0)
        addOption("--fragment-retries", 0)
        addOption("--extractor-retries", 0)
        addOption("--progress-template", PROGRESS_TEMPLATE)
        addOption("--progress-delta", 1)
        addOption("--fixup", "never")
    }

internal fun formatSelector(format: String): String {
    if (!format.matches(Regex("[A-Za-z0-9_.-]+"))) throw DownloadFailure(DownloadError.Refused)
    // yt-dlp treats an unquoted numeric ID as a number, while stored IDs are strings.
    return "all[format_id='$format']"
}

private val TransferFlags =
    listOf(
        "--ignore-config",
        "--no-plugin-dirs",
        "--no-remote-components",
        "--no-update",
        "--no-playlist",
        "--newline",
        "--no-simulate",
        "--no-overwrites",
        "--no-hls-use-mpegts",
        "--abort-on-unavailable-fragments",
    )

internal fun parseProgress(line: String): Progress? {
    val fields = line.removePrefix("perseid|").takeIf { it != line }?.split('|', limit = PROGRESS_FIELDS)
    if (fields?.size != PROGRESS_FIELDS) return null
    val (percent, speed, eta) = fields
    return percent.trim().removeSuffix("%").toFloatOrNull()?.let {
        Progress(
            it.toInt().coerceIn(0, MAX_PERCENT),
            speed.toDoubleOrNull()?.takeIf { rate -> rate > 0 }?.toLong(),
            eta.toLongOrNull()?.takeIf { seconds -> seconds >= 0 },
        )
    }
}

internal fun classifyDownloadError(message: String): DownloadError {
    val text = message.lowercase(Locale.ROOT)
    return ErrorMessages.entries.firstOrNull { (_, patterns) -> patterns.any(text::contains) }?.key
        ?: DownloadError.Unknown
}

private val ErrorMessages =
    mapOf(
        DownloadError.NoSpace to listOf("no space left", "enospc", "disk full"),
        DownloadError.Offline to
            listOf(
                "unreachable",
                "timed out",
                "temporary failure in name",
                "unable to resolve",
                "name or service not known",
                "network is down",
                "failed to resolve",
                "no address associated",
            ),
        DownloadError.Refused to
            listOf(
                "http error",
                "forbidden",
                "unavailable",
                "sign in",
                "requested format",
                "permission denied",
            ),
    )

private val MediaExtensions =
    setOf("mp4", "m4a", "webm", "weba", "mkv", "3gp", "m4v", "aac", "mp3", "opus", "ogg", "flac", "wav", "ts")
private const val PROGRESS_FIELDS = 3
private const val PROGRESS_TEMPLATE = "download:perseid|%(progress._percent_str)s|%(progress.speed)s|%(progress.eta)s"
private const val SOCKET_TIMEOUT_SECONDS = 15
internal const val MAX_PERCENT = 100
