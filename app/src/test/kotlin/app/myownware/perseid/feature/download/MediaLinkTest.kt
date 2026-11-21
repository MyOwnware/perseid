package app.myownware.perseid.feature.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaLinkTest {
    @Test
    fun extractsFirstLinkFromSharedText() {
        val input = "Watch this! (https://example.org/watch?v=12). Another link: https://other.example/video"
        assertEquals("https://example.org/watch?v=12", firstSharedLink(input))
    }

    @Test
    fun skipsInvalidCandidatesAndStopsAtUnicodeWhitespace() {
        assertEquals("https://example.org/video", firstSharedLink("https:///broken https://example.org/video watch"))
    }

    @Test
    fun validatesOnlyHttpLinksWithHostsAndWithoutCredentials() {
        assertEquals("https://example.org/video", validatedLink(" https://example.org/video "))
        val invalid =
            listOf(
                "example.org",
                "ftp://example.org/video",
                "https:///video",
                "https://user@example.org/video",
            )
        for (text in invalid) {
            assertNull(validatedLink(text))
        }
    }
}
