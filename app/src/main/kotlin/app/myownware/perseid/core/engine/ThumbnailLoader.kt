package app.myownware.perseid.core.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

private const val MAX_BYTES = 2 * 1024 * 1024
private const val MAX_EDGE = 512
private const val TIMEOUT = 4_000

internal suspend fun loadThumbnail(address: String): Bitmap? =
    runInterruptible(Dispatchers.IO) {
        try {
            val uri = URI.create(address)
            if (uri.scheme != "https" || uri.host == null || uri.userInfo != null) null else fetchThumbnail(uri.toURL())
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

private fun fetchThumbnail(url: URL): Bitmap? {
    val connection = url.openConnection() as HttpURLConnection
    connection.connectTimeout = TIMEOUT
    connection.readTimeout = TIMEOUT
    connection.useCaches = false
    return try {
        if (connection.contentLengthLong > MAX_BYTES || !connection.contentType.orEmpty().startsWith("image/")) {
            null
        } else {
            connection.inputStream.use(InputStream::boundedBytes)?.let(::decodeThumbnail)
        }
    } finally {
        connection.disconnect()
    }
}

private fun InputStream.boundedBytes(): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val count = read(buffer)
        if (count < 0) break
        if (output.size() + count > MAX_BYTES) return null
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun decodeThumbnail(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val edge = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (edge / sample > MAX_EDGE) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}
