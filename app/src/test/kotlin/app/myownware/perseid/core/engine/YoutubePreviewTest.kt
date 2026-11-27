package app.myownware.perseid.core.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class YoutubePreviewTest {
    @Test
    fun acceptsPlayablePreferredFormatsAndKeepsHigherResolutions() {
        val attempts = mutableListOf<String>()
        val metadata = playableMetadata()
        val result =
            runBlocking {
                youtubePreviewMetadata { client ->
                    attempts += client
                    metadata
                }
            }

        assertEquals(listOf("visionos"), attempts)
        assertEquals(metadata, result.first)
        val formats = result.first.mediaOptions()
        assertEquals(listOf(2160, 1080), formats.video.map { it.height })
    }

    @Test
    fun fallsBackWhenPreferredFormatsLackCompatibleAudio() {
        val metadata =
            readMetadata("""{"title":"Video","formats":[{"format_id":"vp9","height":1080,"vcodec":"vp9"}]}""")
        val result = runBlocking { youtubePreviewMetadata { if (it == "visionos") metadata else playableMetadata() } }

        assertEquals("default", result.second)
    }

    @Test
    fun fallsBackWhenPreferredExtractionFails() {
        val result =
            runBlocking {
                youtubePreviewMetadata { client ->
                    if (client == "visionos") throw PreviewFailure(PreviewError.Unknown)
                    playableMetadata()
                }
            }

        assertEquals("default", result.second)
    }

    @Test
    fun cancellationDoesNotStartTheFallback() {
        val attempts = mutableListOf<String>()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                youtubePreviewMetadata { client ->
                    attempts += client
                    throw CancellationException()
                }
            }
        }
        assertEquals(listOf("visionos"), attempts)
    }

    private fun playableMetadata() =
        readMetadata(
            """
            {"title":"Video","formats":[
              {"format_id":"v","height":1080,"ext":"mp4","vcodec":"avc1","acodec":"none"},
              {"format_id":"a","ext":"m4a","vcodec":"none","acodec":"mp4a.40.2"},
              {"format_id":"4k","height":2160,"ext":"mp4","vcodec":"hvc1","acodec":"none"}
            ]}
            """.trimIndent(),
        )
}
