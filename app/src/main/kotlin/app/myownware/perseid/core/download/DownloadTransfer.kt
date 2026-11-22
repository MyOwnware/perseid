package app.myownware.perseid.core.download

import app.myownware.perseid.core.engine.TrackMerge
import java.io.File

internal suspend fun transferTracks(
    engine: TransferEngine,
    job: DownloadJob,
    progress: (Progress) -> Unit,
    merge: suspend (TrackMerge, List<File>, File) -> File,
): File {
    val directory = File(job.stagingPath)
    if (!directory.mkdirs() && !directory.isDirectory) throw DownloadFailure(DownloadError.Save)
    val mode = job.merge
    val merged = mode?.let { mergedFile(it, directory) }?.takeIf { it.isFile && it.length() > 0 }
    return merged ?: transferSelectedTracks(engine, job, progress, merge, directory)
}

private suspend fun transferSelectedTracks(
    engine: TransferEngine,
    job: DownloadJob,
    progress: (Progress) -> Unit,
    merge: suspend (TrackMerge, List<File>, File) -> File,
    directory: File,
): File {
    val formats = job.formatIds.lines()
    val tracks =
        formats.mapIndexed { index, format ->
            engine.fetch(job, format, index) {
                progress(it.copy(percent = (index * MAX_PERCENT + it.percent) / formats.size))
            }
        }
    val mode = job.merge ?: return tracks.single()
    return merge(mode, tracks, directory)
}
