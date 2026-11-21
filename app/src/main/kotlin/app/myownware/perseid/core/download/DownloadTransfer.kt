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
