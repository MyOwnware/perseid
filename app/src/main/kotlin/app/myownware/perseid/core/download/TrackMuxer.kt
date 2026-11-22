package app.myownware.perseid.core.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import app.myownware.perseid.core.engine.TrackMerge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal suspend fun muxTracks(
    mode: TrackMerge,
    inputs: List<File>,
    directory: File,
): File =
    runInterruptible(Dispatchers.IO) {
        val output = mergedFile(mode, directory)
        val temporary = File(directory, "merging.${output.extension}")
        val muxFormat =
            when (mode) {
                TrackMerge.Mp4 -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                TrackMerge.WebM -> MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            }
        val muxer = MediaMuxer(temporary.path, muxFormat)
        try {
            muxTracks(muxer, inputs, mode)
        } finally {
            muxer.release()
        }
        if (Thread.interrupted()) throw InterruptedException()
        Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE)
        output
    }

internal fun mergedFile(
    mode: TrackMerge,
    directory: File,
): File = File(directory, if (mode == TrackMerge.Mp4) "merged.mp4" else "merged.webm")

private fun muxTracks(
    muxer: MediaMuxer,
    inputs: List<File>,
    mode: TrackMerge,
) {
    withExtractor(inputs[0]) { video ->
        withExtractor(inputs[1]) { audio ->
            val videoMimes =
                if (mode == TrackMerge.Mp4) setOf("video/avc", "video/hevc") else setOf("video/x-vnd.on2.vp9")
            val audioMimes = if (mode == TrackMerge.Mp4) setOf("audio/mp4a-latm") else setOf("audio/opus")
            val videoFormat = video.selectTrack(videoMimes)
            if (mode == TrackMerge.Mp4 && videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                muxer.setOrientationHint(videoFormat.getInteger(MediaFormat.KEY_ROTATION))
            }
            val videoTrack = muxer.addTrack(videoFormat)
            val audioTrack = muxer.addTrack(audio.selectTrack(audioMimes))
            muxer.start()
            writeTracks(muxer, video, videoTrack, audio, audioTrack)
            muxer.stop()
        }
    }
}

private fun MediaExtractor.selectTrack(mimes: Set<String>): MediaFormat {
    val index =
        (0 until trackCount).firstOrNull { getTrackFormat(it).getString(MediaFormat.KEY_MIME) in mimes }
            ?: throw DownloadFailure(DownloadError.Merge)
    selectTrack(index)
    return getTrackFormat(index)
}

private fun writeTracks(
    muxer: MediaMuxer,
    video: MediaExtractor,
    videoTrack: Int,
    audio: MediaExtractor,
    audioTrack: Int,
) {
    val info = MediaCodec.BufferInfo()
    var buffer = ByteBuffer.allocateDirect(0)
    while (video.sampleTime >= 0 || audio.sampleTime >= 0) {
        if (Thread.interrupted()) throw InterruptedException()
        val useVideo = audio.sampleTime < 0 || (video.sampleTime >= 0 && video.sampleTime <= audio.sampleTime)
        val source = if (useVideo) video else audio
        val size = source.sampleSize
        if (size !in 1..Int.MAX_VALUE.toLong()) throw DownloadFailure(DownloadError.Merge)
        if (buffer.capacity() < size) buffer = ByteBuffer.allocateDirect(size.toInt())
        buffer.clear()
        val count = source.readSampleData(buffer, 0)
        check(count == size.toInt())
        val sync = source.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
        val flags = if (sync) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        info.set(0, count, source.sampleTime, flags)
        muxer.writeSampleData(if (useVideo) videoTrack else audioTrack, buffer, info)
        source.advance()
    }
}

internal fun <T> withExtractor(
    file: File,
    action: (MediaExtractor) -> T,
): T {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(file.path)
        return action(extractor)
    } finally {
        extractor.release()
    }
}

internal suspend fun verifyDirectVideo(file: File) =
    runInterruptible(Dispatchers.IO) {
        val valid =
            try {
                withExtractor(file) { extractor ->
                    (0 until extractor.trackCount).any {
                        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                    }
                }
            } catch (_: java.io.IOException) {
                false
            }
        if (!valid) throw DownloadFailure(DownloadError.Refused)
    }
