package app.myownware.perseid.feature.download

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import app.myownware.perseid.R

@Composable
internal fun downloadAction(onDownload: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val start by rememberUpdatedState(onDownload)
    var explain by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }
    if (explain) {
        NotificationExplanation { request ->
            explain = false
            notificationPreferences(context).edit { putBoolean(NOTIFICATION_EXPLAINED, true) }
            if (request && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                start()
            }
        }
    }
    return {
        if (shouldExplainNotifications(context)) explain = true else start()
    }
}

@Composable
private fun NotificationExplanation(onChoice: (Boolean) -> Unit) {
    val colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    AlertDialog(
        onDismissRequest = { onChoice(false) },
        title = { Text(stringResource(R.string.download_notifications_title)) },
        text = { Text(stringResource(R.string.download_notifications_explanation)) },
        confirmButton = {
            TextButton(onClick = { onChoice(true) }, colors = colors) {
                Text(stringResource(R.string.download_notifications_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = { onChoice(false) }, colors = colors) {
                Text(stringResource(R.string.download_notifications_skip))
            }
        },
    )
}

private fun shouldExplainNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
        !notificationPreferences(context).getBoolean(NOTIFICATION_EXPLAINED, false)

private fun notificationPreferences(context: Context) = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)

private const val NOTIFICATION_EXPLAINED = "notification_explained"
