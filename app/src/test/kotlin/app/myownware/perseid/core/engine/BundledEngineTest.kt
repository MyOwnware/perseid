package app.myownware.perseid.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledEngineTest {
    @Test
    fun newerBundleReplacesOlderEngine() {
        assertTrue(shouldInstallBundled("2026.9.30", "2026.10.1"))
        assertTrue(shouldInstallBundled("2025.11.12", "2026.10.26"))
    }

    @Test
    fun bundleKeepsAnEqualOrNewerInstalledEngine() {
        assertFalse(shouldInstallBundled("2026.10.26", "2026.10.26"))
        assertFalse(shouldInstallBundled("2027.1.1", "2026.10.26"))
    }
}
