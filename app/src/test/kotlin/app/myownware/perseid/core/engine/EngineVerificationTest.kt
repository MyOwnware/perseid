package app.myownware.perseid.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class EngineVerificationTest {
    private val artifacts = File("src/main/assets/engine")
    private val key = File(artifacts, "yt-dlp-public.key").readBytes()
    private val checksums = fixture("SHA2-256SUMS")
    private val signature = fixture("SHA2-256SUMS.sig")

    @Test
    fun authenticReleaseMatchesTheArchive() {
        val expected = verifiedEngineChecksum(checksums, signature, key)
        verifyEngineArchive(File(artifacts, "yt-dlp"), expected, "2026.10.26")
    }

    @Test
    fun modifiedChecksumListAndWrongSignatureAreRejected() {
        assertVerificationFailure { verifiedEngineChecksum(checksums.copyOf(checksums.size - 1), signature, key) }
        assertVerificationFailure { verifiedEngineChecksum(checksums, signature.copyOf(signature.size - 64), key) }
    }

    @Test
    fun wrongChecksumAndReplayUnderAnotherVersionAreRejected() {
        val asset = File(artifacts, "yt-dlp")
        val expected = verifiedEngineChecksum(checksums, signature, key)
        assertVerificationFailure { verifyEngineArchive(asset, "0".repeat(expected.length), "2026.10.26") }
        assertVerificationFailure { verifyEngineArchive(asset, expected, "2026.10.27") }
    }

    private fun fixture(name: String): ByteArray {
        val resource = requireNotNull(javaClass.getResourceAsStream("/engine/$name"))
        return resource.use { it.readBytes() }
    }

    private fun assertVerificationFailure(action: () -> Unit) {
        val failure = assertThrows(EngineUpdateFailure::class.java) { action() }
        assertEquals(EngineProblem.Verification, failure.problem)
    }
}
