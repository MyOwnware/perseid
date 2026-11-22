package app.myownware.perseid.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import app.myownware.perseid.BuildConfig
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DesignSize
import app.myownware.perseid.core.design.SecondaryButton

@Composable
internal fun PrivacyDialog(onClose: () -> Unit) {
    SettingsDialog(stringResource(R.string.settings_privacy), onClose) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DesignSize.Small),
        ) {
            Text(stringResource(R.string.settings_privacy_summary))
            Text(stringResource(R.string.settings_privacy_storage))
            Text(stringResource(R.string.settings_privacy_network))
        }
    }
}

@Composable
internal fun AboutDialog(
    engineVersion: String,
    onClose: () -> Unit,
) {
    val browser = LocalUriHandler.current
    SettingsDialog(stringResource(R.string.settings_about), onClose) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DesignSize.Small),
        ) {
            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
            Text(stringResource(R.string.engine_installed, engineVersion))
            Text(stringResource(R.string.settings_about_license))
            SecondaryButton(stringResource(R.string.settings_about_source), onClick = { browser.openUri(REPOSITORY) })
            SecondaryButton(stringResource(R.string.settings_about_issue), onClick = {
                browser.openUri("$REPOSITORY/issues")
            })
        }
    }
}

private const val REPOSITORY = "https://github.com/MyOwnware/perseid"
