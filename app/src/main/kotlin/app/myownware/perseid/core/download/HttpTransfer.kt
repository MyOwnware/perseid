package app.myownware.perseid.core.download

import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

internal data class HttpSource(
    val url: URL,
    val length: Long?,
    val identity: String,
    val headers: Map<String, String> = emptyMap(),
)

private data class RangeReply(
    val last: Long,
    val length: Long,
)

private data class Checkpoint(
    val identity: String,
    val length: Long?,
    val etag: String?,
)

internal class HttpTransfer(
    private val source: HttpSource,
    private val target: File,
    private val checkSpace: (Long) -> Unit = {},
) {
    private val partial = File(target.parentFile, "${target.name}.part")
    private val checkpoint = File(target.parentFile, "${target.name}.checkpoint")

    fun download(progress: (Progress) -> Unit): File {
        val offset = partial.length()
        val existing = if (offset > 0) loadCheckpoint(offset) else null
        if (existing != null) verifyPrefix(existing, offset)
        if (existing?.length != offset) append(existing, progress)
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        checkpoint.delete()
        return target
    }

    private fun loadCheckpoint(offset: Long): Checkpoint {
        if (!checkpoint.isFile) cannotResume()
        val values = Properties().apply { checkpoint.inputStream().use(::load) }
        val saved =
            Checkpoint(
                values.getProperty("identity").orEmpty(),
                values.getProperty("length")?.toLongOrNull(),
                values.getProperty("etag"),
            )
        if (saved.identity != source.identity || saved.length?.let { offset > it } == true) cannotResume()
        if (source.length != null && saved.length != null && source.length != saved.length) cannotResume()
        return saved
    }

    private fun verifyPrefix(
        saved: Checkpoint,
        offset: Long,
    ) {
        val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
        val remote = ByteArray(DEFAULT_BUFFER_SIZE)
        partial.inputStream().use { local ->
            var first = 0L
            while (first < offset) {
                val last = minOf(first + CHUNK_SIZE - 1, offset - 1)
                request(first, last, saved.etag) { connection ->
                    val range = connection.range(first, last, saved.length)
                    if (range.last != last || !verifyValidator(connection, saved)) cannotResume()
                    if (saved.etag != null) return
                    connection.inputStream.use { stream ->
                        comparePrefix(local, stream, bytes, remote, last - first + 1)
                    }
                }
                first = last + 1
            }
        }
    }

    private fun append(
        saved: Checkpoint?,
        progress: (Progress) -> Unit,
    ) {
        var current = saved
        val startedAt = System.nanoTime()
        val initial = partial.length()
        var lastUpdate = startedAt - PROGRESS_INTERVAL_NS
        do {
            val offset = partial.length()
            current =
                appendChunk(current, offset, initial) { bytes, expected ->
                    val now = System.nanoTime()
                    if (now - lastUpdate >= PROGRESS_INTERVAL_NS || bytes == expected) {
                        progress(transferProgress(bytes, expected, initial, startedAt))
                        lastUpdate = now
                    }
                }
        } while (current.length?.let { partial.length() < it } == true)
    }

    private fun appendChunk(
        saved: Checkpoint?,
        offset: Long,
        initial: Long,
        progress: (Long, Long?) -> Unit,
    ): Checkpoint {
        val expected = saved?.length ?: source.length
        val last = offset + CHUNK_SIZE - 1
        return request(offset, last, saved?.etag) { connection ->
            val range =
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    null
                } else {
                    connection.range(offset, last, expected)
                }
            val total = range?.length ?: connection.contentLengthLong.takeIf { it >= 0 }
            val next = refreshCheckpoint(connection, saved, expected, total)
            if (offset == initial) next.length?.let { checkSpace(it - offset) }
            if (saved != next) writeCheckpoint(next)
            val count = range?.let { it.last - offset + 1 } ?: next.length
            copyResponse(connection.inputStream, count) { bytes -> progress(offset + bytes, next.length) }
            next
        }
    }

    private fun refreshCheckpoint(
        connection: HttpURLConnection,
        saved: Checkpoint?,
        expected: Long?,
        total: Long?,
    ): Checkpoint {
        if (expected != null && total != null && expected != total) cannotResume()
        if (saved != null && !verifyValidator(connection, saved)) cannotResume()
        return saved?.copy(length = total ?: expected)
            ?: Checkpoint(source.identity, total ?: expected, connection.strongEtag())
    }

    private fun copyResponse(
        input: InputStream,
        limit: Long?,
        progress: (Long) -> Unit,
    ) {
        input.use { stream ->
            partial.outputStreamForAppend().use { out -> copyBytes(stream, out, limit, progress) }
        }
    }

    private fun copyBytes(
        input: InputStream,
        output: OutputStream,
        limit: Long?,
        progress: (Long) -> Unit,
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

    private fun writeCheckpoint(value: Checkpoint) {
        val values =
            Properties().apply {
                setProperty("identity", value.identity)
                value.length?.let { setProperty("length", it.toString()) }
                value.etag?.let { setProperty("etag", it) }
            }
        val temp = File(checkpoint.parentFile, "${checkpoint.name}.tmp")
        temp.outputStream().use { values.store(it, null) }
        Files.move(
            temp.toPath(),
            checkpoint.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private inline fun <T> request(
        first: Long,
        last: Long,
        etag: String?,
        block: (HttpURLConnection) -> T,
    ): T {
        checkInterrupted()
        val connection = source.url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.useCaches = false
            source.headers.forEach(connection::setRequestProperty)
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Range", "bytes=$first-$last")
            etag?.let { connection.setRequestProperty("If-Range", it) }
            handleResponseCode(connection.responseCode, first)
            val encoding = connection.getHeaderField("Content-Encoding")
            if (encoding != null && !encoding.equals("identity", ignoreCase = true)) cannotResume()
            return block(connection)
        } finally {
            connection.disconnect()
        }
    }
}

private fun File.outputStreamForAppend() = java.io.FileOutputStream(this, true)

private fun HttpURLConnection.strongEtag(): String? {
    val header = getHeaderField("ETag")
    return header?.takeIf { it.startsWith('"') && it.endsWith('"') }
}

private fun verifyValidator(
    connection: HttpURLConnection,
    checkpoint: Checkpoint,
): Boolean = checkpoint.etag == null || connection.strongEtag() == checkpoint.etag

private fun HttpURLConnection.range(
    first: Long,
    last: Long,
    length: Long?,
): RangeReply {
    val parts = getHeaderField("Content-Range")?.let(RangePattern::matchEntire)?.groupValues ?: cannotResume()
    val (start, endText, totalText) = parts.drop(1)
    val end = endText.toLongOrNull() ?: cannotResume()
    val total = totalText.toLongOrNull() ?: cannotResume()
    if (start.toLongOrNull() != first || end !in first..last || end >= total) cannotResume()
    if (length != null && total != length) cannotResume()
    return RangeReply(end, total)
}

private fun comparePrefix(
    local: InputStream,
    remote: InputStream,
    left: ByteArray,
    right: ByteArray,
    length: Long,
) {
    var remaining = length
    while (remaining > 0) {
        checkInterrupted()
        val count = minOf(remaining, left.size.toLong()).toInt()
        java.io.DataInputStream(local).readFully(left, 0, count)
        java.io.DataInputStream(remote).readFully(right, 0, count)
        if (!left.regionMatches(right, count)) cannotResume()
        remaining -= count
    }
}

private fun ByteArray.regionMatches(
    other: ByteArray,
    count: Int,
): Boolean = (0 until count).all { this[it] == other[it] }

private fun checkInterrupted() {
    if (Thread.interrupted()) throw InterruptedException()
}

private fun handleResponseCode(
    code: Int,
    first: Long,
) {
    val error =
        when {
            code == HttpURLConnection.HTTP_PARTIAL || (code == HttpURLConnection.HTTP_OK && first == 0L) -> null
            code == HttpURLConnection.HTTP_OK || code == HTTP_RANGE_NOT_SATISFIABLE -> DownloadError.CannotResume
            code in ExpiredCodes -> DownloadError.Expired
            code == HTTP_TOO_MANY_REQUESTS || code >= HttpURLConnection.HTTP_INTERNAL_ERROR -> DownloadError.Transient
            else -> DownloadError.Refused
        }
    if (error != null) throw DownloadFailure(error)
}

private fun transferProgress(
    current: Long,
    total: Long?,
    initial: Long,
    startedAt: Long,
): Progress {
    val seconds = (System.nanoTime() - startedAt).toDouble() / NANOS_PER_SECOND
    val speed = ((current - initial) / seconds).toLong().takeIf { it > 0 && seconds > 0 }
    val percent = if (total != null && total > 0) (current.toDouble() / total * MAX_PERCENT).toInt() else 0
    val eta = if (total != null && speed != null) (total - current).coerceAtLeast(0) / speed else null
    return Progress(percent.coerceIn(0, MAX_PERCENT), speed, eta)
}

private fun cannotResume(): Nothing = throw DownloadFailure(DownloadError.CannotResume)

private val RangePattern = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
private val ExpiredCodes =
    setOf(HttpURLConnection.HTTP_FORBIDDEN, HttpURLConnection.HTTP_GONE, HttpURLConnection.HTTP_UNAUTHORIZED)
private const val HTTP_RANGE_NOT_SATISFIABLE = 416
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val CHUNK_SIZE = 8L * 1024 * 1024
private const val TIMEOUT = 15_000
private const val PROGRESS_INTERVAL_NS = 500_000_000L
private const val NANOS_PER_SECOND = 1_000_000_000.0
