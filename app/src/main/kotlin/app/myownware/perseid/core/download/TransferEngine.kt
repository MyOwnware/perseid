package app.myownware.perseid.core.download

import android.content.Context
import app.myownware.perseid.core.engine.BundledEngine
import app.myownware.perseid.core.engine.metadataText
import app.myownware.perseid.core.engine.readMetadata
import com.fasterxml.jackson.databind.JsonNode
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.IOException
import java.net.URI
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
            completedTrack(directory, index) ?: fetchTrack(job, format, index, progress)
        }

    private fun fetchTrack(
        job: DownloadJob,
        format: String,
        index: Int,
        progress: (Progress) -> Unit,
    ): File {
        val directory = File(job.stagingPath)
        val staged = directory.listFiles().orEmpty().any { it.name.startsWith("track-$index.") && it.length() > 0 }
        return try {
            BundledEngine.prepare(context)
            val source = resolveTrack(job, format, index)
            val file = File(directory, "track-$index.${source.extension}")
            val http = source.http
            if (http != null) {
                checkPartial(file, staged)
                HttpTransfer(http, file) { requireStorage(context, directory, it) }.download(progress)
            } else {
                if (staged) throw DownloadFailure(DownloadError.CannotResume)
                fetchFragmented(job, format, index, progress)
            }
        } catch (failure: YoutubeDLException) {
            val message = failure.message.orEmpty()
            val error =
                if (staged && message.contains("requested format", ignoreCase = true)) {
                    DownloadError.CannotResume
                } else {
                    classifyDownloadError(message)
                }
            throw DownloadFailure(error, failure)
        }
    }

    private fun resolveTrack(
        job: DownloadJob,
        format: String,
        index: Int,
    ): ResolvedTrack {
        val request =
            baseRequest(job.url, format).apply {
                addOption("--skip-download")
                addOption("--no-check-formats")
                addOption("--dump-single-json")
            }
        val metadata = readMetadata(YoutubeDL.execute(request, "${job.id}-$index-metadata").out)
        return resolveTrack(metadata, format)
    }
}

private data class ResolvedTrack(
    val extension: String,
    val http: HttpSource?,
)

private fun resolveTrack(
    metadata: JsonNode,
    format: String,
): ResolvedTrack {
    val selected = metadata.path("formats").firstOrNull { it.metadataText("format_id") == format } ?: metadata
    val extension =
        selected.metadataText("ext")?.takeIf { it in MediaExtensions }
            ?: throw DownloadFailure(DownloadError.Refused)
    val protocol = selected.metadataText("protocol")
    val http = if (protocol == "https" || protocol == "http") selected.httpSource(metadata) else null
    return ResolvedTrack(extension, http)
}

private fun JsonNode.httpSource(metadata: JsonNode): HttpSource {
    val uri = metadataText("url")?.let(URI::create)
    if (uri?.scheme !in setOf("http", "https")) throw DownloadFailure(DownloadError.Refused)
    val url = requireNotNull(uri).toURL()
    val headers = path("http_headers").properties().filter { (name, _) -> name in ForwardHeaders }
    val sourceId = metadata.metadataText("id")
    val identity = listOf(metadata.metadataText("extractor_key"), sourceId, metadataText("format_id"))
    return HttpSource(
        url,
        path("filesize").asLong(-1).takeIf { it > 0 },
        identity.joinToString("|"),
        headers.associate { (name, value) -> name to value.asText() },
    )
}

private fun checkPartial(
    output: File,
    staged: Boolean,
) {
    val partial = File(output.parentFile, "${output.name}.part")
    if (staged && partial.length() == 0L) throw DownloadFailure(DownloadError.CannotResume)
}

private fun fetchFragmented(
    job: DownloadJob,
    format: String,
    index: Int,
    progress: (Progress) -> Unit,
): File {
    val directory = File(job.stagingPath)
    val request = transferRequest(job.url, format, File(directory, "track-$index.%(ext)s"))
    YoutubeDL.execute(request, "${job.id}-$index") { _, _, line -> parseProgress(line)?.let(progress) }
    return completedTrack(directory, index) ?: throw DownloadFailure(DownloadError.Refused)
}

private fun completedTrack(
    directory: File,
    index: Int,
): File? =
    directory.listFiles().orEmpty().singleOrNull {
        it.isFile && it.length() > 0 && it.nameWithoutExtension == "track-$index" && it.extension in MediaExtensions
    }

private val ForwardHeaders = setOf("User-Agent", "Accept", "Accept-Language", "Referer", "Origin")

private fun transferRequest(
    url: String,
    format: String,
    output: File,
): YoutubeDLRequest =
    baseRequest(url, format).apply {
        addOption("--output", output.absolutePath)
        addOption("--fragment-retries", 0)
        addOption("--progress-template", PROGRESS_TEMPLATE)
        addOption("--progress-delta", 1)
        addOption("--fixup", "never")
    }

private fun baseRequest(
    url: String,
    format: String,
): YoutubeDLRequest =
    YoutubeDLRequest(url).apply {
        for (flag in TransferFlags) addOption(flag)
        addOption("--format", formatSelector(format))
        addOption("--socket-timeout", SOCKET_TIMEOUT_SECONDS)
        addOption("--retries", 0)
        addOption("--extractor-retries", 0)
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
