package app.myownware.perseid.core.download

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths

internal class UnfinishedStorage(
    private val database: DownloadDatabase,
    private val scheduling: Mutex,
) {
    suspend fun size(): Long =
        withContext(Dispatchers.IO) {
            scheduling.withLock {
                database.withTransaction { unfinishedBytes(database.storage().unfinished()) }
            }
        }

    suspend fun clear() =
        withContext(NonCancellable + Dispatchers.IO) {
            scheduling.withLock {
                database.withTransaction { clearUnfinished(database.storage().unfinished()) }
            }
        }
}

internal fun unfinishedBytes(jobs: List<DownloadJob>): Long =
    jobs.filter(DownloadJob::canClearPartial).sumOf { job ->
        val folder = Paths.get(job.stagingPath)
        if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) return@sumOf 0L
        Files.walk(folder).use { paths ->
            paths.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }.mapToLong(Files::size).sum()
        }
    }

internal fun clearUnfinished(jobs: List<DownloadJob>) {
    for (job in jobs.filter(DownloadJob::canClearPartial)) {
        val folder = Paths.get(job.stagingPath)
        if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) continue
        Files.walk(folder).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}

private fun DownloadJob.canClearPartial(): Boolean = state == JobState.Failed || state == JobState.Cancelled
