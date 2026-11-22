package app.myownware.perseid.core.preferences

import android.content.SharedPreferences
import app.myownware.perseid.core.engine.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class AppPreferencesTest {
    @Test
    fun missingOrUnknownPreferencesUseDefaults() {
        val storage = preferenceStorage(mutableMapOf("theme" to "unknown"))
        val defaults = Preferences(AppTheme.System, MediaType.Video, VideoQuality.UpTo1080)
        assertEquals(defaults, AppPreferences(storage).state.value)
    }

    @Test
    fun savedValuesAreImmediatelyVisibleAndSurviveRecreatingTheStore() {
        val storage = preferenceStorage(mutableMapOf("app_language" to "ar"))
        val preferences = AppPreferences(storage)
        preferences.setTheme(AppTheme.Dark)
        preferences.setChoice(MediaType.Audio)
        preferences.setQuality(VideoQuality.UpTo480)
        val expected = Preferences(AppTheme.Dark, MediaType.Audio, VideoQuality.UpTo480)
        assertEquals(expected, preferences.state.value)
        assertEquals(expected, AppPreferences(storage).state.value)
        assertEquals("ar", storage.getString("app_language", null))
    }

    private fun preferenceStorage(values: MutableMap<String, String>): SharedPreferences {
        val editor =
            fake<SharedPreferences.Editor> { proxy, name, args ->
                when (name) {
                    "putString" -> proxy.also { values[args[0] as String] = args[1] as String }
                    "apply" -> null
                    else -> error(name)
                }
            }
        return fake { _, name, args ->
            when (name) {
                "getString" -> values[args[0]] ?: args[1]
                "edit" -> editor
                else -> error(name)
            }
        }
    }
}

private inline fun <reified T : Any> fake(crossinline call: (Any, String, Array<out Any?>) -> Any?): T =
    checkNotNull(
        T::class.java.cast(
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
                call(proxy, method.name, args.orEmpty())
            },
        ),
    )
