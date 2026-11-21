package app.myownware.perseid.feature.download

import app.myownware.perseid.core.engine.MediaPreview
import app.myownware.perseid.core.engine.PreviewEngine
import app.myownware.perseid.core.engine.PreviewError
import app.myownware.perseid.core.engine.PreviewFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadViewModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val engine = FakeEngine()
    private val model = DownloadViewModel(engine, scope)

    @After
    fun close() {
        scope.cancel()
    }

    @Test
    fun preparesOnlyAfterRequestAndShowsProgressAndMetadata() {
        model.editLink("https://example.org/video")
        assertEquals(PreviewState.Empty, model.state.value.preview)
        assertEquals(0, engine.starts)
        model.requestPreview()
        assertEquals(PreviewState.Preparing, model.state.value.preview)
        engine.initialized.complete(Unit)
        assertEquals(PreviewState.Loading, model.state.value.preview)
        engine.result.complete(MediaPreview("Example"))
        assertEquals(PreviewState.Ready(MediaPreview("Example")), model.state.value.preview)
    }

    @Test
    fun editingCancelsPreviewAndLeavesTheNewLinkUntouched() {
        model.paste("Look at https://example.org/video")
        engine.initialized.complete(Unit)
        model.editLink("https://other.example/media")
        engine.result.complete(MediaPreview("Stale"))
        assertTrue(engine.cancelled)
        assertEquals(PreviewState.Empty, model.state.value.preview)
        assertEquals("https://other.example/media", model.state.value.link)
    }

    @Test
    fun validatesInputAndShowsEngineErrors() {
        model.paste("not a URL")
        assertEquals(PreviewState.Invalid, model.state.value.preview)
        assertEquals(0, engine.starts)
        model.share("Watch https://example.org/media")
        engine.initialized.complete(Unit)
        engine.result.completeExceptionally(PreviewFailure(PreviewError.SignIn))
        assertEquals(PreviewState.Failed(PreviewError.SignIn), model.state.value.preview)
        assertEquals(1, model.state.value.shareRevision)
    }

    private class FakeEngine : PreviewEngine {
        val initialized = CompletableDeferred<Unit>()
        val result = CompletableDeferred<MediaPreview>()
        var starts = 0
        var cancelled = false

        override suspend fun prepare() {
            starts++
            initialized.await()
        }

        override suspend fun preview(url: String): MediaPreview =
            try {
                result.await()
            } finally {
                if (!result.isCompleted) cancelled = true
            }
    }
}
