package app.myownware.perseid.core.download

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File

internal data class SavedMedia(
    val uri: Uri,
    val mime: String,
)

private const val DOWNLOAD_PATH = "Download/Perseid/"

internal suspend fun reserveExport(
    resolver: ContentResolver,
    media: File,
    job: DownloadJob,
): SavedMedia =
    withContext(Dispatchers.IO) {
        val mime = media.mimeType(job.type)
        val extension = exportExtension(media.extension, mime)
        val attached = job.contentUri?.toUri()?.takeIf { exportPending(resolver, it) != null }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val markerName = "perseid-${job.id}.$extension"
        val uri =
            attached ?: findPendingExport(resolver, collection, markerName)
                ?: insertPending(resolver, collection, markerName, mime)
        SavedMedia(uri, mime)
    }

internal suspend fun publishMedia(
    resolver: ContentResolver,
    media: File,
    job: DownloadJob,
    saved: SavedMedia,
) = runInterruptible(Dispatchers.IO) {
    if (exportPending(resolver, saved.uri) == true) {
        copyPendingFile(resolver, saved.uri, media)
        val extension = exportExtension(media.extension, saved.mime)
        val name = availableName(resolver, job.title, extension)
        val values =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
        if (resolver.update(saved.uri, values, null, null) != 1) throw DownloadFailure(DownloadError.Save)
    }
}

internal fun deletePendingExport(
    resolver: ContentResolver,
    uri: String?,
) {
    val export = uri?.toUri() ?: return
    if (exportPending(resolver, export) == true) resolver.delete(export, null, null)
}

private fun copyPendingFile(
    resolver: ContentResolver,
    uri: Uri,
    media: File,
) {
    val output = resolver.openOutputStream(uri, "rwt") ?: throw DownloadFailure(DownloadError.Save)
    output.use { target -> media.inputStream().use { input -> copyInterruptibly(input, target) } }
}

private fun availableName(
    resolver: ContentResolver,
    title: String,
    extension: String,
): String {
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    for (number in 1..Int.MAX_VALUE) {
        val name = downloadFileName(title, extension, number)
        if (!nameExists(resolver, collection, name)) return name
    }
    throw DownloadFailure(DownloadError.Save)
}

private fun exportPending(
    resolver: ContentResolver,
    uri: Uri,
): Boolean? =
    resolver.query(uri, arrayOf(MediaStore.Downloads.IS_PENDING), null, null, null)?.use {
        if (it.moveToFirst()) it.getInt(0) != 0 else null
    }

private fun findPendingExport(
    resolver: ContentResolver,
    collection: Uri,
    name: String,
): Uri? {
    val columns = arrayOf(MediaStore.Downloads._ID)
    val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?"
    val arguments = arrayOf(name, DOWNLOAD_PATH)
    val cursor =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val query =
                Bundle().apply {
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_ONLY)
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arguments)
                }
            resolver.query(collection, columns, query, null)
        } else {
            resolver.query(includePending(collection), columns, selection, arguments, null)
        }
    return cursor?.use {
        if (it.moveToFirst()) ContentUris.withAppendedId(collection, it.getLong(0)) else null
    }
}

@Suppress("DEPRECATION")
private fun includePending(collection: Uri): Uri = MediaStore.setIncludePending(collection)

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
    return resolver.insert(collection, values) ?: throw DownloadFailure(DownloadError.Save)
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
