package app.myownware.perseid.feature.library

import android.content.ContentResolver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import app.myownware.perseid.core.download.DOWNLOAD_PATH
import app.myownware.perseid.core.download.DownloadJob
import app.myownware.perseid.core.download.LibraryDao
import app.myownware.perseid.core.download.SavedMedia
import app.myownware.perseid.core.engine.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal class LibraryFiles(
    private val dao: LibraryDao,
    private val resolver: ContentResolver,
) {
    private val changes = Mutex()
    val items = dao.library()

    suspend fun reconcile() =
        withContext(Dispatchers.IO) {
            changes.withLock {
                reconcileLibrary(items.first(), ::inspect, ::removeEntry, ::fillLegacyMetadata)
            }
        }

    suspend fun delete(item: DownloadJob): Boolean =
        withContext(Dispatchers.IO) {
            changes.withLock {
                withContext(NonCancellable) {
                    val uri = item.contentUri?.toUri()
                    val removed = removeLibraryFile(inspect(item)) { uri != null && deleteExport(uri) }
                    if (removed) removeEntry(item)
                    removed
                }
            }
        }

    private fun deleteExport(uri: Uri): Boolean =
        try {
            resolver.delete(uri, "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf(DOWNLOAD_PATH)) > 0 ||
                inspect(uri) == LibraryFile.Missing
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

    private fun inspect(item: DownloadJob): LibraryFile {
        val uri = item.contentUri?.toUri() ?: return LibraryFile.Missing
        return inspect(uri)
    }

    private fun inspect(uri: Uri): LibraryFile =
        try {
            val columns = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
            val inLibrary =
                resolver.query(uri, columns, null, null, null)?.use { cursor ->
                    cursor.moveToFirst() && cursor.getString(0) == DOWNLOAD_PATH
                }
            when (inLibrary) {
                false -> {
                    LibraryFile.Missing
                }

                true -> {
                    openExport(uri)
                }

                null -> {
                    LibraryFile.Unavailable
                }
            }
        } catch (_: FileNotFoundException) {
            LibraryFile.Missing
        } catch (_: IOException) {
            LibraryFile.Unavailable
        } catch (_: SecurityException) {
            LibraryFile.Unavailable
        } catch (_: IllegalArgumentException) {
            LibraryFile.Unavailable
        }

    private fun openExport(uri: Uri): LibraryFile =
        resolver.openFileDescriptor(uri, "r")?.use { LibraryFile.Present(it.statSize) }
            ?: LibraryFile.Unavailable

    private suspend fun fillLegacyMetadata(
        item: DownloadJob,
        bytes: Long,
    ) {
        val uri = item.contentUri?.toUri() ?: return
        val metadata = readEmbeddedMetadata(uri)
        dao.fillSavedMetadata(item.id, bytes, metadata?.first, metadata?.second)
    }

    private fun readEmbeddedMetadata(uri: Uri): Pair<String?, Long?>? =
        try {
            resolver.openFileDescriptor(uri, "r")?.use { file ->
                MediaMetadataRetriever().use { media ->
                    media.setDataSource(file.fileDescriptor)
                    val author =
                        media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            ?: media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                    val duration = media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    author to duration?.div(MILLIS_PER_SECOND)
                }
            }
        } catch (_: RuntimeException) {
            null
        } catch (_: IOException) {
            null
        }

    private suspend fun removeEntry(item: DownloadJob) {
        item.thumbnailPath?.let { File(it).delete() }
        dao.removeSaved(item.id)
    }
}

private const val MILLIS_PER_SECOND = 1_000L

internal sealed interface LibraryFile {
    data object Missing : LibraryFile

    data object Unavailable : LibraryFile

    data class Present(
        val size: Long,
    ) : LibraryFile
}

internal suspend fun reconcileLibrary(
    items: List<DownloadJob>,
    inspect: (DownloadJob) -> LibraryFile,
    remove: suspend (DownloadJob) -> Unit,
    recordSize: suspend (DownloadJob, Long) -> Unit,
) {
    for (item in items) {
        when (val status = inspect(item)) {
            LibraryFile.Missing -> remove(item)
            LibraryFile.Unavailable -> Unit
            is LibraryFile.Present -> if (item.sizeBytes == null && status.size >= 0) recordSize(item, status.size)
        }
    }
}

internal fun removeLibraryFile(
    status: LibraryFile,
    delete: () -> Boolean,
): Boolean =
    when (status) {
        LibraryFile.Missing -> true
        LibraryFile.Unavailable -> false
        is LibraryFile.Present -> delete()
    }

internal fun DownloadJob.libraryMedia(): SavedMedia? {
    val uri = contentUri?.toUri() ?: return null
    return SavedMedia(uri, mimeType ?: if (type == MediaType.Video) "video/*" else "audio/*")
}
