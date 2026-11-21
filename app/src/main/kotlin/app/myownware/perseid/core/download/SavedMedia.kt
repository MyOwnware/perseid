package app.myownware.perseid.core.download

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import app.myownware.perseid.core.engine.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

internal data class SavedMedia(
    val uri: Uri,
    val mime: String,
)

private const val DOWNLOAD_PATH = "Download/Perseid/"
private const val MAX_TITLE_CODEPOINTS = 48L

internal suspend fun publishMedia(
    resolver: ContentResolver,
    media: File,
    job: DownloadJob,
): SavedMedia =
    withContext(Dispatchers.IO) {
        val mime = media.mimeType(job.type)
        val extension = exportExtension(media.extension, mime)
        val uri = reserveMedia(resolver, extension, job.title, mime)
        var visible = false
        try {
            val output = resolver.openOutputStream(uri, "w") ?: throw IOException()
            output.use { target -> media.inputStream().use { it.copyTo(target) } }
            val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            if (resolver.update(uri, values, null, null) != 1) throw IOException()
            visible = true
            SavedMedia(uri, mime)
        } finally {
            if (!visible) resolver.delete(uri, null, null)
        }
    }

private fun reserveMedia(
    resolver: ContentResolver,
    extension: String,
    title: String,
    mime: String,
): Uri {
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val column = MediaStore.Downloads.DISPLAY_NAME
    for (number in 1..Int.MAX_VALUE) {
        val name = downloadFileName(title, extension, number)
        if (nameExists(resolver, collection, name)) continue
        val uri = insertPending(resolver, collection, name, mime)
        val actual = currentName(resolver, uri, column)
        if (actual == name) return uri
        resolver.delete(uri, null, null)
        if (actual?.startsWith("${name.substringBeforeLast('.')} (") != true) throw IOException()
    }
    throw IOException()
}

private fun currentName(
    resolver: ContentResolver,
    uri: Uri,
    column: String,
): String? =
    resolver.query(uri, arrayOf(column), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }

private fun insertPending(
    resolver: ContentResolver,
    collection: Uri,
    name: String,
    mime: String,
): Uri {
    val values =
        ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.RELATIVE_PATH, DOWNLOAD_PATH)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
    return resolver.insert(collection, values) ?: throw IOException()
}

private fun exportExtension(
    extension: String,
    mime: String,
): String {
    val types = MimeTypeMap.getSingleton()
    if (types.getMimeTypeFromExtension(extension) == mime) return extension
    return types.getExtensionFromMimeType(mime) ?: extension
}

private fun nameExists(
    resolver: ContentResolver,
    collection: Uri,
    name: String,
): Boolean =
    resolver
        .query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(name, DOWNLOAD_PATH),
            null,
        )?.use { it.moveToFirst() } == true

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

private fun File.mimeType(type: MediaType): String =
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
