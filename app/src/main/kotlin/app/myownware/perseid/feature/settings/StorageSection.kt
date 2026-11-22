package app.myownware.perseid.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DesignSize
import app.myownware.perseid.core.design.SecondaryButton
import app.myownware.perseid.core.download.DOWNLOAD_PATH

@Composable
internal fun StorageSection(storage: StorageViewModel) {
    val state by storage.state.collectAsState()
    var confirm by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(storage) {
        storage.refresh()
        onPauseOrDispose {}
    }
    SettingsSection(stringResource(R.string.settings_storage)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = DesignSize.Small),
            verticalArrangement = Arrangement.spacedBy(DesignSize.Small),
        ) {
            Text(stringResource(R.string.settings_storage_path, DOWNLOAD_PATH.trimEnd('/')))
            val size = state.bytes?.let { Formatter.formatFileSize(LocalContext.current, it) }
            val displaySize = size ?: stringResource(R.string.settings_storage_unknown)
            Text(stringResource(R.string.settings_storage_unfinished, displaySize))
            SecondaryButton(
                stringResource(R.string.settings_storage_clear),
                enabled = (state.bytes ?: 0) > 0 && !state.clearing,
                onClick = { confirm = true },
            )
            if (state.failed) {
                Text(stringResource(R.string.settings_storage_error), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirm) {
        ClearStorageDialog({ confirm = false }) {
            confirm = false
            storage.clear()
        }
    }
}

@Composable
private fun ClearStorageDialog(
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_storage_confirm)) },
        text = { Text(stringResource(R.string.settings_storage_confirm_message)) },
        confirmButton = { SecondaryButton(stringResource(R.string.settings_storage_clear), onClick = onClear) },
        dismissButton = { SecondaryButton(stringResource(R.string.download_cancel_restart), onClick = onDismiss) },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
