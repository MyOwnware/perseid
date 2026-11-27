package app.myownware.perseid.core.download

import app.myownware.perseid.core.engine.readMetadata
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

class HttpSourceTest {
    @Test
    fun carriesFormatHeadersAndScopedCookies() {
        val format =
            readMetadata(
                """
                {
                  "url": "https://v19.example.org/media",
                  "http_headers": {
                    "User-Agent": "format-agent",
                    "Referer": "https://example.org/watch",
                    "Sec-Fetch-Mode": "navigate",
                    "Cookie": "unscoped=discard"
                  },
                  "cookies": "ttwid=first; Domain=.example.org; Path=/; tt_chain_token=\"a/b==\"; Domain=.example.org; Secure"
                }
                """.trimIndent(),
            )
        val source = format.httpSource(readMetadata("""{"id":"clip","http_headers":{"User-Agent":"page-agent"}}"""))

        assertEquals(
            mapOf(
                "User-Agent" to "format-agent",
                "Referer" to "https://example.org/watch",
                "Sec-Fetch-Mode" to "navigate",
                "Cookie" to "ttwid=first; tt_chain_token=a/b==",
            ),
            source.headers,
        )
    }

    @Test
    fun doesNotSendCookiesOrAuthorizationToAnotherOrigin() {
        val destination = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val origin = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val initialCookie = AtomicReference<String?>()
        val redirectedCookie = AtomicReference<String?>()
        val redirectedAuthorization = AtomicReference<String?>()
        destination.createContext("/media") {
            redirectedCookie.set(it.requestHeaders.getFirst("Cookie"))
            redirectedAuthorization.set(it.requestHeaders.getFirst("Authorization"))
            it.sendResponseHeaders(200, -1)
            it.close()
        }
        origin.createContext("/media") {
            initialCookie.set(it.requestHeaders.getFirst("Cookie"))
            it.responseHeaders.set("Location", "http://127.0.0.1:${destination.address.port}/media")
            it.sendResponseHeaders(302, -1)
            it.close()
        }
        try {
            destination.start()
            origin.start()
            val url = URI("http://127.0.0.1:${origin.address.port}/media").toURL()
            val headers = mapOf("Cookie" to "token=value", "Authorization" to "Bearer token")
            val source = HttpSource(url, null, "clip", headers)
            source.connect(0, 5, null).disconnect()
            assertEquals("token=value", initialCookie.get())
            assertNull(redirectedCookie.get())
            assertNull(redirectedAuthorization.get())
        } finally {
            origin.stop(0)
            destination.stop(0)
        }
    }
}
