package app.myownware.perseid.feature.download

import app.myownware.perseid.core.engine.MediaOption
import app.myownware.perseid.core.engine.MediaOptions
import app.myownware.perseid.core.engine.MediaPreview
import app.myownware.perseid.core.engine.MediaType
import app.myownware.perseid.core.preferences.Preferences
import app.myownware.perseid.core.preferences.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadDefaultsTest {
    private val qualities = listOf(2160, 1440, 1080, 600, 500).map { option(it) }

    @Test
    fun usesTheHighestQualityAtOrBelowTheLimitOrTheLowestAboveIt() {
        assertEquals(2, defaultVideoIndex(qualities))
        assertEquals(3, defaultVideoIndex(qualities, VideoQuality.UpTo720))
        assertEquals(4, defaultVideoIndex(qualities, VideoQuality.UpTo480))
        assertEquals(0, defaultVideoIndex(qualities, VideoQuality.Best))
        assertEquals(0, defaultVideoIndex(listOf(option(null))))
        assertEquals(-1, defaultVideoIndex(emptyList()))
    }

    @Test
    fun preselectsTheDefaultTypeWhenAvailableAndFallsBackToTheOtherType() {
        val audio = option(null)
        val both = MediaPreview("Video", options = MediaOptions(qualities, audio))
        val defaults = Preferences(choice = MediaType.Audio, quality = VideoQuality.UpTo720)
        assertEquals(MediaType.Audio, initialPreview(both, "", defaults).type)
        assertEquals(3, initialPreview(both, "", defaults).videoIndex)
        assertEquals(MediaType.Video, initialPreview(both.copy(options = MediaOptions(qualities)), "", defaults).type)
        val audioOnly = both.copy(options = MediaOptions(audio = audio))
        assertEquals(MediaType.Audio, initialPreview(audioOnly, "", Preferences()).type)
    }

    private fun option(height: Int?) = MediaOption(listOf("format"), height, null)
}
