package app.myownware.perseid.core.download

import app.myownware.perseid.core.engine.MediaType
import app.myownware.perseid.core.engine.TrackMerge
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadTransferTest {
    @get:Rule
    val files = TemporaryFolder()

    @Test
    fun transfersBothTracksInOrderBeforeMerging() =
        runBlocking {
            val events = mutableListOf<String>()
            val job = job("299\n258", TrackMerge.Mp4)
            val engine =
                TransferEngine { _, id, index, update ->
                    events += id
                    update(Progress(100, null, null))
                    File(job.stagingPath, "track-$index").apply { writeText(id) }
                }
            val file =
                transferTracks(engine, job, { events += it.percent.toString() }) { mode, input, directory ->
                    assertEquals(TrackMerge.Mp4, mode)
                    File(directory, "joined.mp4").apply { writeText(input.joinToString(",") { it.readText() }) }
                }
            assertEquals(listOf("299", "50", "258", "100"), events)
            assertEquals("299,258", file.readText())
        }

    @Test
    fun passesThroughACompletedDirectFile() =
        runBlocking {
            val media = files.newFile("combined.mp4")
            val engine = TransferEngine { _, _, _, _ -> media }
            assertEquals(media, transferTracks(engine, job("18"), {}) { _, _, _ -> error("Direct file was merged") })
        }

    @Test
    fun keepsPartialFilesWhenTransferFails() =
        runBlocking {
            val job = job("299\n258", TrackMerge.Mp4)
            val partial = File(job.stagingPath, "track-0.part")
            val engine =
                TransferEngine { _, _, _, _ ->
                    partial.writeText("partial")
                    throw DownloadFailure(DownloadError.Offline)
                }
            val failure =
                runCatching {
                    transferTracks(engine, job, {}) { _, _, _ -> error("Incomplete tracks were merged") }
                }.exceptionOrNull()
            assertEquals(DownloadError.Offline, (failure as DownloadFailure).error)
            assertTrue(partial.isFile)
        }

    @Test
    fun classifiesErrorsWithoutPersistingExtractorMessages() {
        assertEquals(DownloadError.NoSpace, classifyDownloadError("[Errno 28] No space left on device"))
        assertEquals(DownloadError.Offline, classifyDownloadError("Connection timed out"))
        assertEquals(DownloadError.Refused, classifyDownloadError("HTTP Error 403: Forbidden"))
        assertEquals(DownloadError.Unknown, classifyDownloadError("Unexpected failure"))
    }

    @Test
    fun cleansFileNamesAndAddsTheRequestedCollisionSuffix() {
        assertEquals("A_B_ (2).mp4", downloadFileName(" .A/B?. ", "mp4", 2))
        assertEquals("Perseid.m4a", downloadFileName(" ... ", "m4a"))
        val title = "🎬".repeat(100)
        assertEquals("🎬".repeat(48) + " (3).webm", downloadFileName(title, "webm", 3))
    }

    @Test
    fun parsesProgressAndAllowsUnknownSpeedAndEta() {
        assertEquals(Progress(42, 12345, 17), parseProgress("perseid| 42.8%|12345.0|17"))
        assertEquals(Progress(100, null, null), parseProgress("perseid|100%|NA|NA"))
        assertEquals(null, parseProgress("[youtube] extracting"))
    }

    @Test
    fun quotesNumericFormatIdsForTheExtractor() {
        assertEquals("all[format_id='299']", formatSelector("299"))
        assertEquals("all[format_id='hls-audio-128000-Audio']", formatSelector("hls-audio-128000-Audio"))
    }

    @Test
    fun reservesTheOnlySlotUntilCleanupOrPublicationFinishes() {
        val queued = job("18")
        assertTrue(queued.canCancel)
        val transferring = queued.copy(state = JobState.Downloading)
        assertTrue(transferring.canCancel)
        val publishing = transferring.copy(state = JobState.Publishing)
        assertTrue(publishing.active)
        assertFalse(publishing.canCancel)
        val cancelling = transferring.copy(state = JobState.Cancelling)
        assertTrue(cancelling.active)
        assertFalse(cancelling.canCancel)
        assertFalse(cancelling.copy(state = JobState.Cancelled).active)
        assertFalse(publishing.copy(state = JobState.Saved).active)
    }

    private fun job(
        formats: String,
        mode: TrackMerge? = null,
    ): DownloadJob =
        DownloadJob(
            "id",
            "https://example.org/video",
            MediaType.Video,
            formats,
            "Video",
            null,
            files.newFolder().path,
            mode,
            0,
        )
}
