package app.myownware.perseid.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.myownware.perseid.BuildConfig
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DesignSize
import app.myownware.perseid.core.design.SecondaryButton

@Composable
internal fun SettingsPlaceholder() {
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(DesignSize.Section)) {
        SettingsSection(stringResource(R.string.settings_appearance)) {
            SettingRow(stringResource(R.string.settings_theme), stringResource(R.string.settings_theme_system))
        }
        SettingsSection(stringResource(R.string.settings_downloads)) {
            SettingRow(stringResource(R.string.settings_default_choice), stringResource(R.string.media_video))
            SettingsDivider()
            SettingRow(
                stringResource(R.string.settings_default_quality),
                stringResource(R.string.settings_quality_default),
            )
        }
        SettingsSection(stringResource(R.string.settings_app)) {
            SettingRow(stringResource(R.string.settings_engine_update))
            SettingsDivider()
            SettingRow(stringResource(R.string.settings_privacy))
            SettingsDivider()
            SettingRow(stringResource(R.string.settings_licenses))
            SettingsDivider()
            SettingRow(stringResource(R.string.settings_about), BuildConfig.VERSION_NAME) { aboutOpen = true }
        }
    }
    if (aboutOpen) AboutDialog { aboutOpen = false }
}

@Composable
private fun AboutDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.app_name)) },
        text = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
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
    onClick: (() -> Unit)? = null,
) {
    val action = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = DesignSize.TouchTarget)
            .then(action)
            .padding(vertical = DesignSize.Small),
        horizontalArrangement = Arrangement.spacedBy(DesignSize.RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (value != null) Text(value, color = secondary)
        if (onClick != null) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = secondary)
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}
