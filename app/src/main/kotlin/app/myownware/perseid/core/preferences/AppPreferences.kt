package app.myownware.perseid.core.preferences

import android.content.SharedPreferences
import androidx.core.content.edit
import app.myownware.perseid.core.engine.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class AppTheme { System, Light, Dark }

internal enum class VideoQuality(
    val heightLimit: Int?,
) {
    UpTo1080(FULL_HD),
    UpTo720(HD),
    UpTo480(SD),
    Best(null),
}

internal data class Preferences(
    val theme: AppTheme = AppTheme.System,
    val choice: MediaType = MediaType.Video,
    val quality: VideoQuality = VideoQuality.UpTo1080,
)

internal class AppPreferences(
    private val storage: SharedPreferences,
) {
    private val values = MutableStateFlow(read())
    val state = values.asStateFlow()

    fun setTheme(theme: AppTheme) = save("theme", theme)

    fun setChoice(choice: MediaType) = save("default_choice", choice)

    fun setQuality(quality: VideoQuality) = save("default_quality", quality)

    private fun save(
        key: String,
        value: Enum<*>,
    ) {
        storage.edit { putString(key, value.name) }
        values.value = read()
    }

    private fun read(): Preferences =
        Preferences(
            storage.option("theme", AppTheme.System),
            storage.option("default_choice", MediaType.Video),
            storage.option("default_quality", VideoQuality.UpTo1080),
        )
}

private inline fun <reified T : Enum<T>> SharedPreferences.option(
    key: String,
    default: T,
): T = getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

private const val FULL_HD = 1080
private const val HD = 720
private const val SD = 480
