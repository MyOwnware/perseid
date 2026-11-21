package app.myownware.perseid.core.locale

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LanguageTest {
    @Test
    fun systemChoiceKeepsSupportedRegion() {
        val canadianFrench = Locale.CANADA_FRENCH
        assertEquals(canadianFrench, effectiveLocale(AppLanguage.System, canadianFrench))
        val egyptianArabic = Locale.forLanguageTag("ar-EG")
        assertEquals(egyptianArabic, effectiveLocale(AppLanguage.System, egyptianArabic))
    }

    @Test
    fun unsupportedSystemLanguageFallsBackToFrench() {
        assertEquals(Locale.FRENCH, effectiveLocale(AppLanguage.System, Locale.GERMAN))
    }

    @Test
    fun explicitChoiceOverridesThePhone() {
        assertEquals(Locale.ENGLISH, effectiveLocale(AppLanguage.English, Locale.FRENCH))
    }

    @Test
    fun percentageFollowsTheLocale() {
        assertEquals("46%", formatPercent(0.46f, Locale.ENGLISH))
        assertEquals("46\u00a0%", formatPercent(0.46f, Locale.FRENCH))
        assertEquals("٤٦٪\u061c", formatPercent(0.46f, Locale.forLanguageTag("ar-EG")))
    }
}
