package app.myownware.perseid.core.download

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI

class HttpTransferTest {
    @get:Rule
    val files = TemporaryFolder()

    @Test
    fun resumesFromAStoredPrefixAndMatchesEveryByte() =
        withServer { server, target ->
            interrupt(server.source(), target)
            val partial = File(target.parentFile, "${target.name}.part")
            val offset = partial.length()
            assertTrue(offset in 1 until server.bytes.size)
            val completed = HttpTransfer(server.source(), target).download {}
            assertTrue(server.ranges.contains("bytes=$offset-${offset + CHUNK_SIZE - 1}"))
            assertArrayEquals(server.bytes, completed.readBytes())
            assertFalse(partial.exists())
        }

    @Test
    fun refusingRangeLeavesTheExistingPartialIntact() =
        withServer { server, target ->
            interrupt(server.source(), target)
            val partial = File(target.parentFile, "${target.name}.part")
            val before = partial.readBytes()
            server.ignoreRanges = true
            val failure = runCatching { HttpTransfer(server.source(), target).download {} }.exceptionOrNull()
            assertEquals(DownloadError.CannotResume, (failure as DownloadFailure).error)
            assertArrayEquals(before, partial.readBytes())
            assertFalse(target.exists())
        }

    @Test
    fun changedBytesWithTheSameIdentityAreRejected() =
        withServer { server, target ->
            interrupt(server.source(), target)
            server.bytes[0] = (server.bytes[0] + 1).toByte()
            val failure = runCatching { HttpTransfer(server.source(), target).download {} }.exceptionOrNull()
            assertEquals(DownloadError.CannotResume, (failure as DownloadFailure).error)
            assertFalse(target.exists())
        }

    @Test
    fun narrowsRefusedRangesWithoutChangingTheOffset() {
        val server = RangeFixture(10 * 1024 * 1024)
        try {
            server.refuseWideRanges = true
            val target = File(files.newFolder(), "track.mp4")
            val completed = HttpTransfer(server.source(), target).download {}
            assertTrue(server.ranges.contains("bytes=8388608-16777215"))
            assertTrue(server.ranges.contains("bytes=8388608-9437183"))
            assertArrayEquals(server.bytes, completed.readBytes())
        } finally {
            server.close()
        }
    }

    private fun interrupt(
        source: HttpSource,
        target: File,
    ) {
        val failure =
            runCatching {
                HttpTransfer(source, target).download {
                    if (it.percent < 100) throw IOException("interrupted")
                }
            }.exceptionOrNull()
        assertTrue(failure is IOException)
    }

    private fun withServer(action: (RangeFixture, File) -> Unit) {
        val server = RangeFixture()
        try {
            action(server, File(files.newFolder(), "track.mp4"))
        } finally {
            server.close()
        }
    }

    private class RangeFixture(
        size: Int = 131_072,
    ) {
        val bytes = ByteArray(size) { (it % 251).toByte() }
        val ranges = mutableListOf<String>()
        var ignoreRanges = false
        var refuseWideRanges = false
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        init {
            server.createContext("/media", ::serve)
            server.start()
        }

        fun source(): HttpSource {
            val url = URI("http://127.0.0.1:${server.address.port}/media").toURL()
            return HttpSource(url, bytes.size.toLong(), "fixture")
        }

        fun close() = server.stop(0)

        private fun serve(exchange: HttpExchange) {
            val range = exchange.requestHeaders.getFirst("Range")
            val (first, last) = range.removePrefix("bytes=").split('-').map(String::toInt)
            ranges += range
            if (refuseWideRanges && first > 0 && last - first >= 1024 * 1024) {
                exchange.sendResponseHeaders(403, -1)
                exchange.close()
            } else if (ignoreRanges) {
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } else {
                val end = minOf(last, bytes.lastIndex)
                val length = end - first + 1
                exchange.responseHeaders.add("Content-Range", "bytes $first-$end/${bytes.size}")
                exchange.sendResponseHeaders(206, length.toLong())
                exchange.responseBody.use { it.write(bytes, first, length) }
            }
        }
    }

    private companion object {
        const val CHUNK_SIZE = 8L * 1024 * 1024
    }
}
