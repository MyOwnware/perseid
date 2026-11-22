package app.myownware.perseid.feature.library

import app.myownware.perseid.core.download.DownloadJob
import app.myownware.perseid.core.download.JobState
import app.myownware.perseid.core.engine.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private val video = saved("Coastal Walk", MediaType.Video, "Sana")
    private val audio = saved("Sea Sounds", MediaType.Audio, "Audio Archive")
    private val items = listOf(video, audio)

    @Test
    fun filtersByMediaTypeWithoutChangingTheOrder() {
        assertEquals(items, items.matching(LibraryFilter.All, ""))
        assertEquals(listOf(video), items.matching(LibraryFilter.Video, ""))
        assertEquals(listOf(audio), items.matching(LibraryFilter.Audio, ""))
    }

    @Test
    fun searchesTitleAndAuthorWithinTheSelectedFilter() {
        assertEquals(listOf(video), items.matching(LibraryFilter.All, " COASTAL "))
        assertEquals(listOf(audio), items.matching(LibraryFilter.Audio, "archive"))
        assertTrue(items.matching(LibraryFilter.Video, "archive").isEmpty())
    }

    @Test
    fun reconciliationRemovesMissingEntriesButKeepsInaccessibleOnes() =
        runBlocking {
            val legacy = saved("Old download", MediaType.Video, null)
            val removed = mutableListOf<DownloadJob>()
            val sizes = mutableMapOf<DownloadJob, Long>()
            reconcileLibrary(
                items + legacy,
                inspect = {
                    when (it) {
                        video -> LibraryFile.Missing
                        audio -> LibraryFile.Unavailable
                        else -> LibraryFile.Present(1234L)
                    }
                },
                remove = { removed += it },
                recordSize = { item, bytes -> sizes[item] = bytes },
            )
            assertEquals(listOf(video), removed)
            assertEquals(mapOf(legacy to 1234L), sizes)
        }

    @Test
    fun deleteDoesNotRemoveTheEntryIfTheFileCannotBeDeleted() {
        assertFalse(removeLibraryFile(LibraryFile.Unavailable) { error("Must not attempt delete") })
        assertFalse(removeLibraryFile(LibraryFile.Present(12L)) { false })
    }

    @Test
    fun deleteCanRemoveTheEntryWhenTheFileIsGoneOrDeletionSucceeds() {
        assertTrue(removeLibraryFile(LibraryFile.Missing) { error("No file to delete") })
        assertTrue(removeLibraryFile(LibraryFile.Present(12L)) { true })
    }

    private fun saved(
        title: String,
        type: MediaType,
        author: String?,
    ) = DownloadJob(
        id = title,
        url = "",
        type = type,
        formatIds = "",
        title = title,
        thumbnailPath = null,
        stagingPath = "",
        merge = null,
        createdAt = 0,
        state = JobState.Saved,
        author = author,
    )
}
