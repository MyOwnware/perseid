package app.myownware.perseid.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DesignSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class License(
    val title: Int,
    val files: List<String>,
) {
    Gpl(R.string.settings_license_gpl, listOf("gpl-3.0.txt")),
    Apache(R.string.settings_license_apache, listOf("apache-2.0.txt", "jvm-notices.txt")),
    Symbols(R.string.settings_license_symbols, listOf("material-symbols.txt")),
    Native(R.string.settings_license_native, listOf("download-engine.txt", "native-licenses.txt")),
    YtDlp(R.string.settings_license_ytdlp, listOf("yt-dlp-license.txt", "yt-dlp-third-party.txt")),
    BouncyCastle(R.string.settings_license_bouncycastle, listOf("bouncycastle.txt")),
}

@Composable
internal fun LicensesDialog(onClose: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<License?>(null) }
    val license = selected
    if (license == null) {
        SettingsDialog(stringResource(R.string.settings_licenses), onClose) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (entry in License.entries) {
                    SettingRow(stringResource(entry.title)) { selected = entry }
                }
            }
        }
    } else {
        LicenseText(license) { selected = null }
    }
}

@Composable
private fun LicenseText(
    license: License,
    onClose: () -> Unit,
) {
    val assets = LocalContext.current.assets
    val notices by produceState(emptyList<String>(), license) {
        value =
            withContext(Dispatchers.IO) {
                license.files.flatMap { file ->
                    assets
                        .open("licenses/$file")
                        .bufferedReader()
                        .use { it.readText() }
                        .split("\n\n")
                }
            }
    }
    SettingsDialog(stringResource(license.title), onClose) {
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(DesignSize.Small),
        ) {
            items(notices.size) { index -> Text(notices[index], style = MaterialTheme.typography.bodySmall) }
        }
    }
}
