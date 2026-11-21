package app.myownware.perseid.core.engine

internal enum class MediaType { Video, Audio }

internal data class MediaSize(
    val bytes: Long,
    val estimated: Boolean,
)

internal data class MediaOption(
    val formatIds: List<String>,
    val height: Int?,
    val size: MediaSize?,
    val requiresFfmpeg: Boolean = false,
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
    setOf("mp4", "webm", "mov", "mkv", "3gp", "flv", "ts", "avi", "m4v", "mpeg", "mpg", "wmv", "ogv")

internal fun mapMediaFormats(formats: List<StreamFormat>): MediaOptions {
    // yt-dlp supplies formats in ascending preference order.
    val bestFirst = formats.asReversed()
    val audio = bestFirst.filter { it.isAudioOnly }
    val preferredAudio = audio.firstOrNull { it.extension == "m4a" } ?: audio.firstOrNull()
    val compatibleAudio = audio.firstOrNull { it.isMp4Audio && it.isAac }
    return MediaOptions(videoOptions(bestFirst, audio, compatibleAudio), preferredAudio?.toOption(null, null))
}

private fun videoOptions(
    bestFirst: List<StreamFormat>,
    audio: List<StreamFormat>,
    compatibleAudio: StreamFormat?,
): List<MediaOption> =
    bestFirst
        .filter { it.isCombined || it.isVideoOnly }
        .groupBy { it.height }
        .entries
        .sortedByDescending { it.key ?: 0 }
        .map { (height, streams) ->
            val compatible = streams.firstOrNull { it.isCompatibleWith(compatibleAudio) }
            val stream = compatible ?: streams.first()
            val companion =
                when {
                    stream.isCombined -> null
                    compatible != null -> compatibleAudio
                    else -> audio.firstOrNull { it.matchesContainer(stream.extension) } ?: audio.firstOrNull()
                }
            stream.toOption(height, companion)
        }

private fun StreamFormat.matchesContainer(videoExtension: String): Boolean =
    extension == videoExtension || (videoExtension == "mp4" && extension == "m4a")

private fun StreamFormat.isCompatibleWith(audio: StreamFormat?): Boolean =
    extension == "mp4" && isH264 && (isAac || (isVideoOnly && audio != null))

private fun StreamFormat.toOption(
    height: Int?,
    audio: StreamFormat?,
): MediaOption {
    if (audio == null) return MediaOption(listOf(id), height, size)
    val total = size?.combinedWith(audio.size)
    // Use API 29 MP4 support as the native muxing baseline.
    val canMuxMp4 = extension == "mp4" && (isH264 || isHevc) && audio.isMp4Audio && audio.isAac
    return MediaOption(listOf(id, audio.id), height, total, requiresFfmpeg = !canMuxMp4)
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
    val requiresFfmpeg: Boolean,
)
