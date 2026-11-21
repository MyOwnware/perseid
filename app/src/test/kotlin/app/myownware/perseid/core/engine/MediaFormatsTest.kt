package app.myownware.perseid.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFormatsTest {
    @Test
    fun youtubeKeepsOnePerResolutionAndPrefersCompatibleStreams() {
        val options = fixture("youtube")
        assertEquals(listOf(2160, 1440, 1080, 720, 480, 360, 240, 144), options.video.map { it.height })
        val fullHd = options.video.first { it.height == 1080 }
        assertEquals(MediaOption(listOf("299", "258"), 1080, MediaSize(288_387_264, false), TrackMerge.Mp4), fullHd)
        assertEquals(MediaOption(listOf("258"), null, MediaSize(30_767_611, false)), options.audio)
        assertEquals(listOf("315", "251"), options.video.first().formatIds)
        assertEquals(listOf("308", "251"), options.video[1].formatIds)
        assertEquals(listOf(TrackMerge.WebM, TrackMerge.WebM), options.video.take(2).map { it.merge })
        assertEquals(MediaSize(28_526_904, true), options.video.first { it.height == 360 }.size)
    }

    @Test
    fun tiktokDeduplicatesCombinedStreamsWithoutAddingAudio() {
        val options = fixture("tiktok")
        assertEquals(
            listOf(
                MediaOption(listOf("bytevc1_720p_1504834-1"), 1280, MediaSize(2_004_627, false)),
                MediaOption(listOf("h264_540p_2240963-1"), 1024, MediaSize(2_953_029, false)),
            ),
            options.video,
        )
        assertNull(options.audio)
    }

    @Test
    fun facebookFallsBackToTheBestCombinedStreamWithoutResolutionOrSize() {
        val options = fixture("facebook")
        assertEquals(listOf(MediaOption(listOf("hd"), null, null)), options.video)
        assertNull(options.audio)
    }

    @Test
    fun xUsesDirectFormatsWhenSplitAudioCodecIsUnknown() {
        val options = fixture("x")
        assertEquals(listOf(720, 360, 270), options.video.map { it.height })
        assertEquals(listOf("http-2176", "http-832", "http-288"), options.video.map { it.formatIds.single() })
        assertEquals(MediaOption(listOf("hls-audio-128000-Audio"), null, null), options.audio)
    }

    @Test
    fun audioOnlyFallsBackToBestOriginalStream() {
        val options =
            parse(
                """{"formats":[
                {"format_id":"low","ext":"webm","vcodec":"none","acodec":"opus","filesize":30},
                {"format_id":"high","ext":"webm","vcodec":"none","acodec":"opus","filesize_approx":100}
            ]}""",
            )
        assertTrue(options.video.isEmpty())
        assertEquals(MediaOption(listOf("high"), null, MediaSize(100, true)), options.audio)
    }

    @Test
    fun singleFormatAtTheTopLevelUsesTheShortEdgeForPortraitVideo() {
        val options =
            parse(
                """{"format_id":"only","ext":"mp4","height":1920,"width":1080,"filesize":0,"filesize_approx":-1}""",
            )
        assertEquals(listOf(MediaOption(listOf("only"), 1080, null)), options.video)
        assertNull(options.audio)
    }

    @Test
    fun splitSizeIsEstimatedWhenEitherStreamIsEstimated() {
        val option = splitOptions("100", """"filesize_approx":25""").video.single()
        assertEquals(MediaSize(125, true), option.size)
        assertEquals(TrackMerge.Mp4, option.merge)
    }

    @Test
    fun splitSizeIsHiddenWhenEitherStreamIsMissingOrWhenSumOverflows() {
        assertNull(splitOptions("100", """"filesize":null""").video.single().size)
        assertNull(splitOptions(Long.MAX_VALUE.toString(), """"filesize":1""").video.single().size)
    }

    @Test
    fun webmMergeUsesMatchingOpusAudio() {
        val options =
            parse(
                """{"formats":[
                {"format_id":"vp9","ext":"webm","height":1440,"vcodec":"vp9","acodec":"none"},
                {"format_id":"aac","ext":"m4a","vcodec":"none","acodec":"aac"},
                {"format_id":"opus","ext":"webm","vcodec":"none","acodec":"opus"}
            ]}""",
            )
        assertEquals(MediaOption(listOf("vp9", "opus"), 1440, null, TrackMerge.WebM), options.video.single())
        assertEquals(listOf("aac"), options.audio?.formatIds)
    }

    @Test
    fun hevcWithAacCanUseNativeMp4Muxing() {
        val options =
            parse(
                """{"formats":[
                {"format_id":"hevc","ext":"mp4","height":1080,"vcodec":"hvc1.1.6","acodec":"none"},
                {"format_id":"aac","ext":"m4a","vcodec":"none","acodec":"aac"}
            ]}""",
            )
        assertEquals(MediaOption(listOf("hevc", "aac"), 1080, null, TrackMerge.Mp4), options.video.single())
    }

    private fun splitOptions(
        videoSize: String,
        audioSize: String,
    ): MediaOptions =
        parse(
            """{"formats":[
                {"format_id":"video","ext":"mp4","height":720,
                "vcodec":"avc1.000000","acodec":"none",
                "filesize":$videoSize},
                {"format_id":"audio","ext":"m4a","vcodec":"none","acodec":"aac",$audioSize}
            ]}""",
        )

    private fun fixture(site: String): MediaOptions {
        val resource = requireNotNull(javaClass.getResource("/formats/$site.json"))
        return parse(resource.readText())
    }

    private fun parse(json: String): MediaOptions = readMetadata(json).mediaOptions()
}
