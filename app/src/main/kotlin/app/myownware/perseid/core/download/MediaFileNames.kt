package app.myownware.perseid.core.download

import android.webkit.MimeTypeMap
import app.myownware.perseid.core.engine.MediaType
import java.io.File
import java.io.InputStream
import java.io.OutputStream

private const val MAX_TITLE_CODEPOINTS = 48L

internal fun exportExtension(
    extension: String,
    mime: String,
): String {
    val types = MimeTypeMap.getSingleton()
    if (types.getMimeTypeFromExtension(extension) == mime) return extension
    return types.getExtensionFromMimeType(mime) ?: extension
}

internal fun downloadFileName(
    title: String,
    extension: String,
    number: Int = 1,
): String {
    val cleaned = title.replace(Regex("""[\p{Cc}<>:"/\\|?*]"""), "_").trim(' ', '.').ifEmpty { "Perseid" }
    val suffix = if (number == 1) "" else " ($number)"
    val prefix = cleaned.codePoints().limit(MAX_TITLE_CODEPOINTS).toArray()
    val base = String(prefix, 0, prefix.size).trimEnd(' ', '.')
    return "$base$suffix.$extension"
}

internal fun File.mimeType(type: MediaType): String =
    when (extension) {
        "mp4", "m4v", "m4a" -> if (type == MediaType.Audio) "audio/mp4" else "video/mp4"
        "webm", "weba" -> if (type == MediaType.Audio) "audio/webm" else "video/webm"
        else -> MimeTypes[extension] ?: throw DownloadFailure(DownloadError.Save)
    }

private val MimeTypes =
    mapOf(
        "opus" to "audio/ogg",
        "ogg" to "audio/ogg",
        "mp3" to "audio/mpeg",
        "aac" to "audio/aac",
        "flac" to "audio/flac",
        "wav" to "audio/wav",
        "3gp" to "video/3gpp",
        "mkv" to "video/x-matroska",
        "ts" to "video/mp2t",
    )

internal fun copyInterruptibly(
    input: InputStream,
    output: OutputStream,
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        if (Thread.interrupted()) throw InterruptedException()
        val count = input.read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
    }
}
