package app.myownware.perseid.feature.download

import java.net.URI

private val sharedUrl = Regex("https?://[^\\s<>\"\\p{Cntrl}\\p{Z}]+", RegexOption.IGNORE_CASE)

internal fun firstSharedLink(text: String): String? =
    sharedUrl.findAll(text).firstNotNullOfOrNull { validatedLink(trimSharedPunctuation(it.value)) }

private fun trimSharedPunctuation(url: String): String {
    val candidate = url.trimEnd('.', ',', ';', '!', '\'', '…')
    val excess = candidate.count { it == ')' } - candidate.count { it == '(' }
    return candidate.dropLast(excess.coerceIn(0, candidate.takeLastWhile { it == ')' }.length))
}

internal fun validatedLink(text: String): String? {
    val url = text.trim()
    val uri =
        try {
            URI(url)
        } catch (_: java.net.URISyntaxException) {
            return null
        }
    return url.takeIf {
        uri.scheme?.lowercase() in setOf("http", "https") && uri.host != null && uri.userInfo == null
    }
}
