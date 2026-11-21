package app.myownware.perseid.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DesignSize

@Composable
internal fun SettingsPlaceholder() {
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
        }
    }
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
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = DesignSize.TouchTarget).padding(vertical = DesignSize.Small),
        horizontalArrangement = Arrangement.spacedBy(DesignSize.RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (value != null) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}
