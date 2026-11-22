package app.myownware.perseid.core.download

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

internal fun copyInterruptibly(
    input: InputStream,
    output: OutputStream,
    limit: Long? = null,
    progress: (Long) -> Unit = {},
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var transferred = 0L
    while (limit == null || transferred < limit) {
        checkInterrupted()
        val available = limit?.minus(transferred) ?: buffer.size.toLong()
        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), available).toInt())
        if (count < 0) break
        output.write(buffer, 0, count)
        transferred += count
        progress(transferred)
    }
    if (limit != null && transferred != limit) throw EOFException()
}

internal fun checkInterrupted() {
    if (Thread.interrupted()) throw InterruptedException()
}
