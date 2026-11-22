package app.myownware.perseid.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.myownware.perseid.BuildConfig
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DesignSize
import app.myownware.perseid.core.design.SecondaryButton
import app.myownware.perseid.core.engine.MediaType
import app.myownware.perseid.core.locale.AppLanguage
import app.myownware.perseid.core.locale.formatVideoResolution
import app.myownware.perseid.core.preferences.AppPreferences
import app.myownware.perseid.core.preferences.AppTheme
import app.myownware.perseid.core.preferences.Preferences
import app.myownware.perseid.core.preferences.VideoQuality

private enum class Setting(
    val title: Int,
) {
    Theme(R.string.settings_theme),
    Language(R.string.settings_language),
    Choice(R.string.settings_default_choice),
    Quality(R.string.settings_default_quality),
    Engine(R.string.settings_engine_update),
    Privacy(R.string.settings_privacy),
    Licenses(R.string.settings_licenses),
    About(R.string.settings_about),
}

@Composable
internal fun SettingsScreen(
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    engine: EngineUpdateViewModel,
    preferences: AppPreferences,
    storage: StorageViewModel,
) {
    val state by engine.state.collectAsState()
    val selected by preferences.state.collectAsState()
    LaunchedEffect(engine) { engine.refresh() }
    DisposableEffect(engine) { onDispose(engine::close) }
    var open by rememberSaveable { mutableStateOf<Setting?>(null) }
    SettingsRows(selected, language, state.installed, storage) {
        open = it
        if (it == Setting.Engine) engine.check()
    }
    val setting = open ?: return
    val close: () -> Unit = { open = null }
    val title = stringResource(setting.title)
    when (setting) {
        Setting.Language -> {
            OptionsDialog(title, AppLanguage.entries.map { it to stringResource(it.label) }, language, close) {
                close()
                onLanguage(it)
            }
        }

        Setting.Theme -> {
            OptionsDialog(title, AppTheme.entries.map { it to themeLabel(it) }, selected.theme, close) {
                preferences.setTheme(it)
                close()
            }
        }

        Setting.Choice -> {
            OptionsDialog(title, MediaType.entries.map { it to mediaLabel(it) }, selected.choice, close) {
                preferences.setChoice(it)
                close()
            }
        }

        Setting.Quality -> {
            OptionsDialog(title, VideoQuality.entries.map { it to qualityLabel(it) }, selected.quality, close) {
                preferences.setQuality(it)
                close()
            }
        }

        Setting.Engine -> {
            EngineUpdateDialog(engine, state) {
                engine.close()
                close()
            }
        }

        Setting.Privacy -> {
            PrivacyDialog(close)
        }

        Setting.Licenses -> {
            LicensesDialog(close)
        }

        Setting.About -> {
            AboutDialog(state.installed, close)
        }
    }
}

@Composable
private fun SettingsRows(
    selected: Preferences,
    language: AppLanguage,
    version: String,
    storage: StorageViewModel,
    onOpen: (Setting) -> Unit,
) {
    val appearance =
        listOf(
            Setting.Theme to themeLabel(selected.theme),
            Setting.Language to stringResource(language.label),
        )
    val defaults =
        listOf(
            Setting.Choice to mediaLabel(selected.choice),
            Setting.Quality to qualityLabel(selected.quality),
        )
    val app =
        listOf(
            Setting.Engine to version,
            Setting.Privacy to null,
            Setting.Licenses to null,
            Setting.About to BuildConfig.VERSION_NAME,
        )
    Column(verticalArrangement = Arrangement.spacedBy(DesignSize.Section)) {
        SettingsGroup(R.string.settings_appearance, appearance, onOpen)
        SettingsGroup(R.string.settings_downloads, defaults, onOpen)
        StorageSection(storage)
        SettingsGroup(R.string.settings_app, app, onOpen)
    }
}

@Composable
private fun SettingsGroup(
    title: Int,
    items: List<Pair<Setting, String?>>,
    onOpen: (Setting) -> Unit,
) {
    SettingsSection(stringResource(title)) {
        for ((index, entry) in items.withIndex()) {
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            val (item, value) = entry
            SettingRow(stringResource(item.title), value) { onOpen(item) }
        }
    }
}

@Composable
private fun themeLabel(theme: AppTheme): String =
    stringResource(
        when (theme) {
            AppTheme.System -> R.string.settings_theme_system
            AppTheme.Light -> R.string.settings_theme_light
            AppTheme.Dark -> R.string.settings_theme_dark
        },
    )

@Composable
private fun mediaLabel(type: MediaType): String =
    stringResource(
        if (type == MediaType.Video) R.string.media_video else R.string.media_audio,
    )

@Composable
private fun qualityLabel(quality: VideoQuality): String {
    val limit = quality.heightLimit ?: return stringResource(R.string.settings_quality_best)
    val resolution = formatVideoResolution(LocalContext.current, limit)
    return stringResource(R.string.settings_quality_default, resolution)
}

@Composable
private fun <T> OptionsDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onClose: () -> Unit,
    onSelect: (T) -> Unit,
) {
    SettingsDialog(title, onClose) {
        Column(Modifier.selectableGroup()) {
            for ((choice, label) in options) {
                val active = choice == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = DesignSize.TouchTarget)
                        .selectable(active, role = Role.RadioButton, onClick = { onSelect(choice) }),
                    horizontalArrangement = Arrangement.spacedBy(DesignSize.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.onSurface)
                    RadioButton(active, onClick = null, colors = colors)
                    Text(label)
                }
            }
        }
    }
}

@Composable
internal fun SettingsDialog(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = content,
        confirmButton = { SecondaryButton(stringResource(R.string.settings_close), onClick = onClose) },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = DesignSize.Tiny).semantics { heading() },
        )
        content()
    }
}

@Composable
internal fun SettingRow(
    label: String,
    value: String? = null,
    onClick: () -> Unit,
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = DesignSize.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = DesignSize.Small),
        horizontalArrangement = Arrangement.spacedBy(DesignSize.RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (value != null) Text(value, Modifier.weight(1f, fill = false), color = secondary)
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = secondary)
    }
}
