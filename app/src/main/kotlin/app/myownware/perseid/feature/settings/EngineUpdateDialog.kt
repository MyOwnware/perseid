package app.myownware.perseid.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DesignSize
import app.myownware.perseid.core.design.SecondaryButton
import app.myownware.perseid.core.engine.EngineProblem

@Composable
internal fun EngineUpdateDialog(
    model: EngineUpdateViewModel,
    state: EngineUpdateState,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.settings_engine_update)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DesignSize.Small)) {
                Text(versionText(state))
                state.message()?.let { Text(stringResource(it)) }
                state.previous?.takeUnless { state.busy }?.let { version ->
                    SecondaryButton(stringResource(R.string.engine_restore, version), onClick = model::restore)
                }
            }
        },
        confirmButton = { EngineUpdateAction(model, state) },
        dismissButton = { SecondaryButton(stringResource(R.string.settings_close), onClick = onClose) },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun EngineUpdateAction(
    model: EngineUpdateViewModel,
    state: EngineUpdateState,
) {
    if (state.status == EngineStatus.Available) {
        SecondaryButton(stringResource(R.string.engine_confirm), onClick = model::confirm)
    } else if (state.status in setOf(EngineStatus.Idle, EngineStatus.Failed, EngineStatus.Restored)) {
        SecondaryButton(stringResource(R.string.engine_check), onClick = model::check)
    }
}

@Composable
private fun versionText(state: EngineUpdateState): String {
    val available = state.available?.takeIf { state.status == EngineStatus.Available }
    return if (available != null) {
        stringResource(R.string.engine_versions, state.installed, available)
    } else {
        stringResource(R.string.engine_installed, state.installed)
    }
}

private fun EngineUpdateState.message(): Int? =
    when (status) {
        EngineStatus.Idle -> null
        EngineStatus.Checking -> R.string.engine_checking
        EngineStatus.Available -> R.string.engine_available
        EngineStatus.UpToDate -> R.string.engine_up_to_date
        EngineStatus.Updating -> R.string.engine_updating
        EngineStatus.Waiting -> R.string.engine_waiting
        EngineStatus.Updated -> R.string.engine_updated
        EngineStatus.Restored -> R.string.engine_restored
        EngineStatus.Failed -> problem.message()
    }

private fun EngineProblem?.message(): Int =
    when (this) {
        EngineProblem.Offline -> R.string.engine_offline
        EngineProblem.Verification -> R.string.engine_verification_failed
        EngineProblem.Storage -> R.string.engine_storage_failed
        else -> R.string.engine_network_failed
    }
