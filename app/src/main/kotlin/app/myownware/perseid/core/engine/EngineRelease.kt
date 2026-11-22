package app.myownware.perseid.core.engine

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI

internal enum class EngineProblem { Offline, Network, Verification, Storage }

internal class EngineUpdateFailure(
    val problem: EngineProblem,
) : IOException()

internal class EngineRelease(
    private val publicKey: ByteArray,
) {
    fun latest(): String {
        val release = ObjectMapper().readTree(fetch("$API/releases/latest", MAX_METADATA_BYTES))
        val tag = release.path("tag_name").asText()
        if (!isEngineVersion(tag)) throw EngineUpdateFailure(EngineProblem.Network)
        return tag
    }

    fun download(
        version: String,
        target: File,
    ) {
        require(isEngineVersion(version))
        val release = "$REPO/releases/download/$version"
        val checksums = fetch("$release/SHA2-256SUMS", MAX_METADATA_BYTES)
        val signature = fetch("$release/SHA2-256SUMS.sig", MAX_METADATA_BYTES)
        val checksum = verifiedEngineChecksum(checksums, signature, publicKey)
        target.outputStream().use { downloadTo("$release/yt-dlp", it, MAX_ARCHIVE_BYTES) }
        verifyEngineArchive(target, checksum, version)
    }
}

private fun fetch(
    url: String,
    limit: Long,
): ByteArray =
    java.io.ByteArrayOutputStream().use {
        downloadTo(url, it, limit)
        it.toByteArray()
    }

private fun downloadTo(
    url: String,
    output: OutputStream,
    limit: Long,
) {
    var location = URI.create(url)
    repeat(MAX_REDIRECTS) {
        val connection = githubConnection(location)
        try {
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Perseid")
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { copyBounded(it, output, limit) }
                return
            }
            val redirect = connection.getHeaderField("Location")?.takeIf { status in REDIRECT_CODES }
            location = location.resolve(redirect ?: throw EngineUpdateFailure(EngineProblem.Network))
        } finally {
            connection.disconnect()
        }
    }
    throw EngineUpdateFailure(EngineProblem.Network)
}

private fun githubConnection(location: URI): HttpURLConnection {
    if (location.scheme != "https" || location.host !in GITHUB_HOSTS || location.userInfo != null) {
        throw EngineUpdateFailure(EngineProblem.Network)
    }
    return location.toURL().openConnection() as HttpURLConnection
}

private fun copyBounded(
    input: java.io.InputStream,
    output: OutputStream,
    limit: Long,
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        if (Thread.interrupted()) throw InterruptedException()
        val count = input.read(buffer)
        if (count == -1) return
        total += count
        if (total > limit) throw EngineUpdateFailure(EngineProblem.Network)
        output.write(buffer, 0, count)
    }
}

private const val API = "https://api.github.com/repos/yt-dlp/yt-dlp"
private const val REPO = "https://github.com/yt-dlp/yt-dlp"
private const val MAX_METADATA_BYTES = 64L * 1024
private const val MAX_ARCHIVE_BYTES = 12L * 1024 * 1024
private const val TIMEOUT_MS = 15_000
private const val MAX_REDIRECTS = 5
private const val TEMPORARY_REDIRECT = 307
private const val PERMANENT_REDIRECT = 308
private val REDIRECT_CODES =
    setOf(
        HttpURLConnection.HTTP_MOVED_PERM,
        HttpURLConnection.HTTP_MOVED_TEMP,
        HttpURLConnection.HTTP_SEE_OTHER,
        TEMPORARY_REDIRECT,
        PERMANENT_REDIRECT,
    )
private val GITHUB_HOSTS =
    setOf(
        "api.github.com",
        "github.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com",
    )
