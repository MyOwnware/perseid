package app.myownware.perseid.core.engine

internal fun classifyPreviewError(message: String): PreviewError {
    val text = message.lowercase()
    val signIn = listOf("private", "sign in", "log in", "login required", "members-only")
    return when {
        listOf(
            "name resolution",
            "no route to host",
            "network is unreachable",
            "unable to resolve host",
            "no internet",
            "nodename nor servname",
        ).any(text::contains) -> PreviewError.Offline

        listOf("unsupported url", "no suitable extractor").any(text::contains) -> PreviewError.Unsupported

        signIn.any(text::contains) -> PreviewError.SignIn

        listOf("404", "not found", "has been removed", "no longer available", "video unavailable")
            .any(text::contains) -> PreviewError.NotFound

        else -> PreviewError.Unknown
    }
}
