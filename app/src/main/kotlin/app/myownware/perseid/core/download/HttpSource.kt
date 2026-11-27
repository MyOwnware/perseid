package app.myownware.perseid.core.download

import app.myownware.perseid.core.engine.metadataText
import com.fasterxml.jackson.databind.JsonNode
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

internal data class HttpSource(
    val url: URL,
    val length: Long?,
    val identity: String,
    val headers: Map<String, String> = emptyMap(),
)

internal fun JsonNode.httpSource(metadata: JsonNode): HttpSource {
    val uri = metadataText("url")?.let(URI::create)
    if (uri?.scheme !in setOf("http", "https")) throw DownloadFailure(DownloadError.Refused)
    val url = requireNotNull(uri).toURL()
    val headers =
        path("http_headers")
            .properties()
            .filter { (name, value) -> !name.equals("Cookie", ignoreCase = true) && value.isTextual }
            .associate { (name, value) -> name to value.asText() }
    // yt-dlp scopes this serialized cookie list to the format URL.
    val cookies = metadataText("cookies")?.let(::cookieHeader).orEmpty()
    val sourceId = metadata.metadataText("id")
    val identity = listOf(metadata.metadataText("extractor_key"), sourceId, metadataText("format_id"))
    return HttpSource(
        url,
        path("filesize").asLong(-1).takeIf { it > 0 },
        identity.joinToString("|"),
        if (cookies.isEmpty()) headers else headers + ("Cookie" to cookies),
    )
}

private fun cookieHeader(cookies: String): String =
    CookiePairs
        .findAll(cookies)
        .filter { it.groupValues[1].lowercase(Locale.ROOT) !in CookieAttributes }
        .mapNotNull {
            val value = it.groupValues[2].removeSurrounding("\"")
            if (CookieValue.matches(value)) "${it.groupValues[1]}=$value" else null
        }.joinToString("; ")

private val CookiePairs = Regex("""(?:^|; )([^;=\s]+)=("(?:\\.|[^"\\])*"|[^;]*)""")
private val CookieValue = Regex("""[\x21\x23-\x2B\x2D-\x3A\x3C-\x5B\x5D-\x7E]*""")
private val CookieAttributes = setOf("domain", "path", "expires", "version")

internal fun HttpSource.connect(
    first: Long,
    last: Long,
    etag: String?,
): HttpURLConnection {
    var address = url
    repeat(MAX_REDIRECTS + 1) {
        val connection = address.openConnection() as HttpURLConnection
        var connected = false
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.useCaches = false
            val requestHeaders =
                when {
                    !address.sameOrigin(url) -> headers.filterKeys { it in RedirectHeaders }
                    address.path != url.path -> headers - "Cookie"
                    else -> headers
                }
            requestHeaders.forEach(connection::setRequestProperty)
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Range", "bytes=$first-$last")
            etag?.let { connection.setRequestProperty("If-Range", it) }
            connected = connection.responseCode !in RedirectCodes
            if (connected) return connection
            val location = connection.getHeaderField("Location") ?: throw DownloadFailure(DownloadError.Refused)
            address = address.redirectTo(location)
        } finally {
            if (!connected) connection.disconnect()
        }
    }
    throw DownloadFailure(DownloadError.Refused)
}

private fun URL.redirectTo(location: String): URL =
    URL(this, location).also { destination ->
        if (destination.protocol !in HttpProtocols || destination.userInfo != null) {
            throw DownloadFailure(DownloadError.Refused)
        }
    }

private fun URL.sameOrigin(other: URL): Boolean =
    protocol == other.protocol && host.equals(other.host, ignoreCase = true) &&
        (if (port == -1) defaultPort else port) == (if (other.port == -1) other.defaultPort else other.port)

private val RedirectHeaders = setOf("User-Agent", "Accept", "Accept-Language", "Referer", "Origin", "Sec-Fetch-Mode")
private val HttpProtocols = setOf("http", "https")
private val RedirectCodes =
    setOf(
        HttpURLConnection.HTTP_MOVED_PERM,
        HttpURLConnection.HTTP_MOVED_TEMP,
        HttpURLConnection.HTTP_SEE_OTHER,
        HTTP_TEMPORARY_REDIRECT,
        HTTP_PERMANENT_REDIRECT,
    )
private const val HTTP_TEMPORARY_REDIRECT = 307
private const val HTTP_PERMANENT_REDIRECT = 308
private const val MAX_REDIRECTS = 5

private const val TIMEOUT = 15_000
