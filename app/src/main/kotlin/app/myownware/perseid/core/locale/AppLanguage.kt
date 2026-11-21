package app.myownware.perseid.core.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import app.myownware.perseid.R
import java.util.Locale

private const val LANGUAGE_PREFERENCES = "language_preferences"
private const val LANGUAGE_CHOICE = "app_language"

internal enum class AppLanguage(
    val tag: String,
    val label: Int,
) {
    System("", R.string.settings_theme_system),
    French("fr", R.string.settings_language_french),
    Arabic("ar", R.string.settings_language_arabic),
    English("en", R.string.settings_language_english),
}

internal fun effectiveLocale(
    choice: AppLanguage,
    systemLocale: Locale,
): Locale {
    if (choice != AppLanguage.System) return Locale.forLanguageTag(choice.tag)
    return if (AppLanguage.entries.any { it.tag == systemLocale.language }) systemLocale else Locale.FRENCH
}

internal fun appLanguage(context: Context): AppLanguage {
    val tag =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales[0]?.language
        } else {
            context.getSharedPreferences(LANGUAGE_PREFERENCES, Context.MODE_PRIVATE).getString(LANGUAGE_CHOICE, null)
        }
    return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.System
}

internal fun localizedContext(context: Context): Context {
    val systemLocales =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).systemLocales
        } else {
            Resources.getSystem().configuration.locales
        }
    val locale = effectiveLocale(appLanguage(context), systemLocales[0] ?: Locale.FRENCH)
    if (context.resources.configuration.locales[0] == locale) return context
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    return context.createConfigurationContext(config)
}

internal fun changeAppLanguage(
    context: Context,
    choice: AppLanguage,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(choice.tag)
    } else {
        context
            .getSharedPreferences(LANGUAGE_PREFERENCES, Context.MODE_PRIVATE)
            .edit { putString(LANGUAGE_CHOICE, choice.tag) }
    }
}
