package app.myownware.perseid.core.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EngineStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun restorePersistsAcrossReopenAndRetainsTheOtherVersion() {
        val directory = folder.newFolder("engine")
        val active = File(folder.newFolder("wrapper"), "yt-dlp")
        val store = EngineStore(directory, active)
        val old = "2025.11.12"
        val current = "2026.10.26"
        store.save(old, "old".byteInputStream())
        store.save(current, "current".byteInputStream())
        store.select(EngineSelection(current, old, current))
        assertEquals("current", active.readText())
        store.restore()
        val restarted = EngineStore(directory, active)
        val selected = requireNotNull(restarted.selection())
        assertEquals(EngineSelection(old, current, current), selected)
        restarted.sync(selected)
        assertEquals("old", active.readText())
        restarted.restore()
        assertEquals("current", active.readText())
    }

    @Test
    fun activationWaitsUntilTheDownloadReleasesItsLock() =
        runBlocking {
            val execution = Mutex(locked = true)
            val waiting = CompletableDeferred<Unit>()
            val activated = CompletableDeferred<Unit>()
            val operation = launch { whenIdle(execution, { waiting.complete(Unit) }) { activated.complete(Unit) } }
            waiting.await()
            assertFalse(activated.isCompleted)
            execution.unlock()
            operation.join()
            activated.await()
        }
}
