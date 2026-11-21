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
import app.myownware.perseid.core.locale.AppLanguage
import app.myownware.perseid.core.locale.formatVideoResolution

private const val DEFAULT_QUALITY = 1080

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
internal fun SettingsPlaceholder(
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf<Setting?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(DesignSize.Section)) {
        SettingsGroup(R.string.settings_appearance, listOf(Setting.Theme, Setting.Language), language) { open = it }
        SettingsGroup(R.string.settings_downloads, listOf(Setting.Choice, Setting.Quality), language) { open = it }
        val appItems = listOf(Setting.Engine, Setting.Privacy, Setting.Licenses, Setting.About)
        SettingsGroup(R.string.settings_app, appItems, language) { open = it }
    }
    val setting = open ?: return
    if (setting == Setting.Language) {
        LanguageDialog(language, onClose = { open = null }) { choice ->
            open = null
            onLanguage(choice)
        }
    } else {
        SettingInfoDialog(setting) { open = null }
    }
}

@Composable
private fun SettingsGroup(
    title: Int,
    items: List<Setting>,
    language: AppLanguage,
    onOpen: (Setting) -> Unit,
) {
    SettingsSection(stringResource(title)) {
        for ((index, item) in items.withIndex()) {
            if (index > 0) SettingsDivider()
            SettingRow(stringResource(item.title), settingValue(item, language)) { onOpen(item) }
        }
    }
}

@Composable
private fun settingValue(
    item: Setting,
    language: AppLanguage,
): String? =
    when (item) {
        Setting.Theme -> {
            stringResource(R.string.settings_theme_system)
        }

        Setting.Language -> {
            stringResource(language.label)
        }

        Setting.Choice -> {
            stringResource(R.string.media_video)
        }

        Setting.Quality -> {
            val resolution = formatVideoResolution(LocalContext.current, DEFAULT_QUALITY)
            stringResource(R.string.settings_quality_default, resolution)
        }

        Setting.About -> {
            BuildConfig.VERSION_NAME
        }

        else -> {
            null
        }
    }

@Composable
private fun SettingInfoDialog(
    setting: Setting,
    onClose: () -> Unit,
) {
    val title = if (setting == Setting.About) R.string.app_name else setting.title
    val message =
        when (setting) {
            Setting.About -> stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)
            Setting.Privacy -> stringResource(R.string.settings_privacy_summary)
            Setting.Licenses -> stringResource(R.string.settings_licenses_summary)
            else -> stringResource(R.string.settings_later)
        }
    SettingsDialog(stringResource(title), onClose) { Text(message) }
}

@Composable
private fun LanguageDialog(
    language: AppLanguage,
    onClose: () -> Unit,
    onSelect: (AppLanguage) -> Unit,
) {
    SettingsDialog(stringResource(R.string.settings_language), onClose) {
        Column(Modifier.selectableGroup()) {
            for (choice in AppLanguage.entries) {
                LanguageOption(choice, choice == language) { onSelect(choice) }
            }
        }
    }
}

@Composable
private fun LanguageOption(
    choice: AppLanguage,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = DesignSize.TouchTarget)
            .selectable(selected, role = Role.RadioButton, onClick = onSelect),
        horizontalArrangement = Arrangement.spacedBy(DesignSize.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.onSurface)
        RadioButton(selected, onClick = null, colors = colors)
        Text(stringResource(choice.label))
    }
}

@Composable
private fun SettingsDialog(
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
private fun SettingsSection(
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
private fun SettingRow(
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
        if (value != null) Text(value, color = secondary)
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = secondary)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}
