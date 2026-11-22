package app.myownware.perseid.core.download

import app.myownware.perseid.core.engine.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UnfinishedDownloadsTest {
    @get:Rule val files = TemporaryFolder()

    @Test
    fun onlyFailedAndCancelledDownloadsCountAndCanBeCleared() {
        val jobs =
            JobState.entries.map { state ->
                val folder = files.newFolder(state.name)
                folder.resolve("partial").writeBytes(byteArrayOf(1, 2, 3))
                job(state, folder.path)
            }
        assertEquals(6L, unfinishedBytes(jobs))
        clearUnfinished(jobs)
        for (job in jobs) {
            val partial = java.io.File(job.stagingPath, "partial")
            if (job.state == JobState.Failed || job.state == JobState.Cancelled) {
                assertFalse(partial.exists())
            } else {
                assertTrue(partial.exists())
            }
        }
        assertEquals(0L, unfinishedBytes(jobs))
    }

    private fun job(
        state: JobState,
        path: String,
    ) = DownloadJob(state.name, "", MediaType.Video, "", "", null, path, null, 0, state)
}
