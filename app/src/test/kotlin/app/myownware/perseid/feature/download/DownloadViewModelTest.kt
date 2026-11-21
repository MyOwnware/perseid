package app.myownware.perseid.feature.download

import app.myownware.perseid.core.engine.DownloadRequest
import app.myownware.perseid.core.engine.MediaOption
import app.myownware.perseid.core.engine.MediaOptions
import app.myownware.perseid.core.engine.MediaPreview
import app.myownware.perseid.core.engine.MediaType
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
import org.junit.Assert.assertNull
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
        val expected = PreviewState.Ready(MediaPreview("Example"), "https://example.org/video")
        assertEquals(expected, model.state.value.preview)
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

    @Test
    fun defaultsToFullHdAndRestoresTheVideoSelectionAfterChoosingAudio() {
        val hd = MediaOption(listOf("hd", "audio"), 1080, null)
        val high = MediaOption(listOf("4k", "audio"), 2160, null, true)
        val audio = MediaOption(listOf("audio"), null, null)
        val thumbnail = "https://example.org/thumbnail"
        val options = MediaOptions(listOf(high, hd), audio)
        finishPreview(MediaPreview("Example", thumbnailUrl = thumbnail, options = options))

        assertEquals(
            hd.formatIds,
            model.state.value
                .downloadRequest()
                ?.formatIds,
        )
        model.selectVideo(0)
        model.selectType(MediaType.Audio)
        assertEquals(
            DownloadRequest("https://example.org/video", MediaType.Audio, audio.formatIds, "Example", thumbnail, false),
            model.state.value.downloadRequest(),
        )

        model.selectType(MediaType.Video)
        assertEquals(
            DownloadRequest("https://example.org/video", MediaType.Video, high.formatIds, "Example", thumbnail, true),
            model.state.value.downloadRequest(),
        )
    }

    @Test
    fun defaultsToLowestWhenEveryResolutionIsAboveFullHd() {
        val high = MediaOption(listOf("4k"), 2160, null)
        val low = MediaOption(listOf("2k"), 1440, null)
        finishPreview(MediaPreview("Example", options = MediaOptions(listOf(high, low))))
        model.selectType(MediaType.Audio)
        model.selectVideo(-1)
        assertEquals(
            low.formatIds,
            model.state.value
                .downloadRequest()
                ?.formatIds,
        )
    }

    @Test
    fun selectsAudioForAnAudioOnlyPreviewAndClearsEligibilityWhenLinkChanges() {
        val audio = MediaOption(listOf("audio"), null, null)
        assertNull(model.state.value.downloadRequest())
        finishPreview(MediaPreview("Example", options = MediaOptions(audio = audio)))
        assertEquals(
            MediaType.Audio,
            model.state.value
                .downloadRequest()
                ?.type,
        )
        model.editLink("https://other.example/media")
        assertNull(model.state.value.downloadRequest())
    }

    private fun finishPreview(media: MediaPreview) {
        model.paste("Watch https://example.org/video")
        assertNull(model.state.value.downloadRequest())
        engine.initialized.complete(Unit)
        engine.result.complete(media)
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
