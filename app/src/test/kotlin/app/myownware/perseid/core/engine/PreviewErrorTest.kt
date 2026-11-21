package app.myownware.perseid.core.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewErrorTest {
    @Test
    fun mapsEngineFailuresToSafeCategories() {
        val examples =
            mapOf(
                "Temporary failure in name resolution" to PreviewError.Offline,
                "Unsupported URL" to PreviewError.Unsupported,
                "This video is private. Sign in" to PreviewError.SignIn,
                "HTTP Error 404: Not Found" to PreviewError.NotFound,
                "unrecognized extractor response" to PreviewError.Unknown,
            )
        examples.forEach { (message, expected) -> assertEquals(expected, classifyPreviewError(message)) }
    }

    @Test
    fun givesInstagramSignInItsOwnMessage() {
        val message = "Log in to access this content"
        assertEquals(PreviewError.InstagramSignIn, classifyPreviewError(message, "www.instagram.com"))
        assertEquals(PreviewError.SignIn, classifyPreviewError(message, "notinstagram.com"))
        assertEquals(PreviewError.NotFound, classifyPreviewError("HTTP Error 404", "www.instagram.com"))
    }
}
