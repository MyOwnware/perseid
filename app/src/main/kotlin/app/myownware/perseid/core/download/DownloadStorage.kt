package app.myownware.perseid.core.download

import android.content.Context
import android.os.storage.StorageManager
import java.io.File

internal fun requireStorage(
    context: Context,
    directory: File,
    bytes: Long,
) {
    val storage = context.getSystemService(StorageManager::class.java)
    if (storage.getAllocatableBytes(storage.getUuidForPath(directory)) < bytes) {
        throw DownloadFailure(DownloadError.NoSpace)
    }
}
