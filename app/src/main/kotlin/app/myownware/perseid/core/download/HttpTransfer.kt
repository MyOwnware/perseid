package app.myownware.perseid.core.download

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

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
    private var chunkSize = CHUNK_SIZE

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
        if (!checkpoint.isFile) cannotResume(ResumeRefusal.MissingCheckpoint)
        val values = Properties().apply { checkpoint.inputStream().use(::load) }
        val saved =
            Checkpoint(
                values.getProperty("identity").orEmpty(),
                values.getProperty("length")?.toLongOrNull(),
                values.getProperty("etag"),
            )
        if (saved.identity != source.identity) cannotResume(ResumeRefusal.SourceIdentityChanged)
        if (saved.length?.let { offset > it } == true) cannotResume(ResumeRefusal.PartialExceedsCheckpoint)
        if (source.length != null && saved.length != null && source.length != saved.length) {
            cannotResume(ResumeRefusal.SourceLengthChanged)
        }
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
                first =
                    rangeRequest(first, offset - 1, saved.etag) { connection, last ->
                        val range = connection.range(first, last, saved.length)
                        if (range.last != last) cannotResume(ResumeRefusal.PrefixRangeTruncated)
                        if (!verifyValidator(connection, saved)) cannotResume(ResumeRefusal.ValidatorChanged)
                        if (saved.etag != null) return
                        connection.inputStream.use { stream ->
                            comparePrefix(local, stream, bytes, remote, last - first + 1)
                        }
                        last + 1
                    }
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
        return rangeRequest(offset, Long.MAX_VALUE, saved?.etag) { connection, last ->
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
        if (expected != null && total != null && expected != total) cannotResume(ResumeRefusal.SourceLengthChanged)
        if (saved != null && !verifyValidator(connection, saved)) cannotResume(ResumeRefusal.ValidatorChanged)
        return saved?.copy(length = total ?: expected)
            ?: Checkpoint(source.identity, total ?: expected, connection.strongEtag())
    }

    private fun copyResponse(
        input: InputStream,
        limit: Long?,
        progress: (Long) -> Unit,
    ) {
        input.use { stream ->
            partial.outputStreamForAppend().use { out -> copyInterruptibly(stream, out, limit, progress) }
        }
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

    private inline fun <T> rangeRequest(
        first: Long,
        limit: Long,
        etag: String?,
        block: (HttpURLConnection, Long) -> T,
    ): T {
        val last = minOf(limit, first + chunkSize - 1)
        return try {
            request(first, last, etag) { block(it, last) }
        } catch (failure: DownloadFailure) {
            if (failure.cause !is RangeWidthRefused || first == 0L || last - first < SMALL_CHUNK_SIZE) {
                throw failure
            }
            chunkSize = SMALL_CHUNK_SIZE
            val smallerLast = first + chunkSize - 1
            request(first, smallerLast, etag) { block(it, smallerLast) }
        }
    }

    private inline fun <T> request(
        first: Long,
        last: Long,
        etag: String?,
        block: (HttpURLConnection) -> T,
    ): T {
        checkInterrupted()
        val connection = source.connect(first, last, etag)
        try {
            handleResponseCode(connection.responseCode, first)
            val encoding = connection.getHeaderField("Content-Encoding")
            if (encoding != null && !encoding.equals("identity", ignoreCase = true)) {
                cannotResume(ResumeRefusal.UnexpectedEncoding)
            }
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
    val header = getHeaderField("Content-Range")
    val range = header?.let(RangePattern::matchEntire) ?: cannotResume(ResumeRefusal.InvalidContentRange)
    val parts = range.groupValues
    val (start, endText, totalText) = parts.drop(1)
    val end = endText.toLongOrNull() ?: cannotResume(ResumeRefusal.InvalidContentRange)
    val total = totalText.toLongOrNull() ?: cannotResume(ResumeRefusal.InvalidContentRange)
    if (start.toLongOrNull() != first || end !in first..last || end >= total) {
        cannotResume(ResumeRefusal.UnexpectedRangeBounds)
    }
    if (length != null && total != length) cannotResume(ResumeRefusal.SourceLengthChanged)
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
        if (!left.regionMatches(right, count)) cannotResume(ResumeRefusal.PrefixMismatch)
        remaining -= count
    }
}

private fun ByteArray.regionMatches(
    other: ByteArray,
    count: Int,
): Boolean = (0 until count).all { this[it] == other[it] }

private fun handleResponseCode(
    code: Int,
    first: Long,
) {
    val error =
        when {
            code == HttpURLConnection.HTTP_PARTIAL || (code == HttpURLConnection.HTTP_OK && first == 0L) -> null
            code == HttpURLConnection.HTTP_OK -> cannotResume(ResumeRefusal.RangeIgnored)
            code == HTTP_RANGE_NOT_SATISFIABLE -> cannotResume(ResumeRefusal.RangeUnsatisfiable)
            code in ExpiredCodes -> DownloadError.Expired
            code == HTTP_TOO_MANY_REQUESTS || code >= HttpURLConnection.HTTP_INTERNAL_ERROR -> DownloadError.Transient
            else -> DownloadError.Refused
        }
    if (error != null) {
        val cause = if (code == HttpURLConnection.HTTP_FORBIDDEN) RangeWidthRefused() else null
        throw DownloadFailure(error, cause)
    }
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

private val RangePattern = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
private val ExpiredCodes =
    setOf(HttpURLConnection.HTTP_FORBIDDEN, HttpURLConnection.HTTP_GONE, HttpURLConnection.HTTP_UNAUTHORIZED)
private const val HTTP_RANGE_NOT_SATISFIABLE = 416
private const val HTTP_TOO_MANY_REQUESTS = 429

private class RangeWidthRefused : IOException()

private const val SMALL_CHUNK_SIZE = 1024L * 1024
private const val CHUNK_SIZE = 8L * 1024 * 1024
private const val PROGRESS_INTERVAL_NS = 500_000_000L
private const val NANOS_PER_SECOND = 1_000_000_000.0
