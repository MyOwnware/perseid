package app.myownware.perseid.core.engine

internal enum class MediaType { Video, Audio }

internal enum class TrackMerge { Mp4, WebM }

internal data class MediaSize(
    val bytes: Long,
    val estimated: Boolean,
)

internal data class MediaOption(
    val formatIds: List<String>,
    val height: Int?,
    val size: MediaSize?,
    val merge: TrackMerge? = null,
)

internal data class MediaOptions(
    val video: List<MediaOption> = emptyList(),
    val audio: MediaOption? = null,
)

internal data class StreamFormat(
    val id: String,
    val extension: String,
    val videoCodec: String?,
    val audioCodec: String?,
    val height: Int?,
    val size: MediaSize?,
) {
    val canSelectVideo get() = videoCodec?.startsWith("av01") != true && (isCombined || isVideoOnly)
    val isVideoOnly get() = audioCodec == "none" && videoCodec != "none"
    val isAudioOnly
        get() = audioCodec != "none" && (videoCodec == "none" || (videoCodec == null && extension in AudioExtensions))
    val isCombined get() = videoCodec != "none" && audioCodec != "none" && extension in VideoExtensions
    val isMp4Audio get() = extension == "m4a" || extension == "mp4"
    val isAac get() = audioCodec?.let { it.startsWith("mp4a") || it == "aac" } == true
    val isH264 get() = videoCodec?.let { it.startsWith("avc1") || it == "h264" } == true
    val isHevc
        get() =
            videoCodec?.let { codec ->
                codec.startsWith("hvc1") || codec.startsWith("hev1") || codec == "hevc" || codec == "h265"
            } == true
}

private val AudioExtensions = setOf("m4a", "aac", "mp3", "opus", "ogg", "flac", "wav", "weba")

private val VideoExtensions =
    setOf("mp4", "webm", "mkv", "3gp", "m4v")

internal fun mapMediaFormats(formats: List<StreamFormat>): MediaOptions {
    // yt-dlp supplies formats in ascending preference order.
    val bestFirst = formats.asReversed()
    val audio = bestFirst.filter { it.isAudioOnly }
    val preferredAudio = audio.firstOrNull { it.extension == "m4a" } ?: audio.firstOrNull()
    val mp4Audio = audio.firstOrNull { it.isMp4Audio && it.isAac }
    val webmAudio = audio.firstOrNull { it.extension == "webm" && it.audioCodec == "opus" }
    val video =
        bestFirst
            .filter { it.canSelectVideo }
            .groupBy { it.height }
            .entries
            .sortedByDescending { it.key ?: 0 }
            .mapNotNull { (_, streams) -> selectVideo(streams, mp4Audio, webmAudio) }
    return MediaOptions(video, preferredAudio?.toOption())
}

private fun selectVideo(
    streams: List<StreamFormat>,
    mp4Audio: StreamFormat?,
    webmAudio: StreamFormat?,
): MediaOption? =
    streams.firstNotNullOfOrNull { stream -> stream.preferredMp4(mp4Audio) }
        ?: streams.firstOrNull { it.isCombined }?.toOption()
        ?: streams.firstNotNullOfOrNull { stream -> stream.alternatePair(mp4Audio, webmAudio) }

private fun StreamFormat.preferredMp4(audio: StreamFormat?): MediaOption? =
    when {
        extension != "mp4" || !isH264 -> null
        isCombined && isAac -> toOption()
        isVideoOnly && audio != null -> toOption(audio, TrackMerge.Mp4)
        else -> null
    }

private fun StreamFormat.alternatePair(
    mp4Audio: StreamFormat?,
    webmAudio: StreamFormat?,
): MediaOption? =
    when {
        !isVideoOnly -> null
        extension == "mp4" && isHevc && mp4Audio != null -> toOption(mp4Audio, TrackMerge.Mp4)
        extension == "webm" && videoCodec == "vp9" && webmAudio != null -> toOption(webmAudio, TrackMerge.WebM)
        else -> null
    }

private fun StreamFormat.toOption(
    audio: StreamFormat? = null,
    merge: TrackMerge? = null,
): MediaOption =
    if (audio == null) {
        MediaOption(listOf(id), height, size)
    } else {
        MediaOption(listOf(id, audio.id), height, size?.combinedWith(audio.size), merge)
    }

private fun MediaSize.combinedWith(other: MediaSize?): MediaSize? =
    other?.takeIf { bytes <= Long.MAX_VALUE - it.bytes }?.let {
        MediaSize(bytes + it.bytes, estimated || it.estimated)
    }

internal data class DownloadRequest(
    val url: String,
    val type: MediaType,
    val formatIds: List<String>,
    val title: String,
    val thumbnailUrl: String?,
    val merge: TrackMerge?,
)
