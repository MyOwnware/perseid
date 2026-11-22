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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DesignSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LicensesDialog(onClose: () -> Unit) {
    val assets = LocalContext.current.assets
    val notice by produceState("") {
        value =
            withContext(Dispatchers.IO) {
                assets.open("licenses/bouncycastle.txt").bufferedReader().use { it.readText() }
            }
    }
    SettingsDialog(stringResource(R.string.settings_licenses), onClose) {
        Column(
            Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DesignSize.Small),
        ) {
            Text(stringResource(R.string.settings_licenses_summary))
            Text(notice, style = MaterialTheme.typography.bodySmall)
        }
    }
}
