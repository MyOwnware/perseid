package app.myownware.perseid.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DownloadRow
import app.myownware.perseid.core.design.DownloadRowContent
import app.myownware.perseid.core.design.TransferState
import app.myownware.perseid.core.locale.TransferMetrics
import kotlinx.coroutines.launch

private val ActiveMetrics = TransferMetrics(bytesPerSecond = 2_100_000, secondsRemaining = 38)

private const val ACTIVE_PROGRESS = 0.46f
private const val PAUSED_PROGRESS = 0.28f
private const val WAITING_PROGRESS = 0.62f

private enum class ExampleDownload(
    val title: Int,
    val progress: Float,
    val initialState: TransferState,
) {
    Active(R.string.preview_active_title, ACTIVE_PROGRESS, TransferState.Downloading),
    Queued(R.string.preview_queued_title, 0f, TransferState.Queued),
    Paused(R.string.preview_paused_title, PAUSED_PROGRESS, TransferState.Paused),
    Waiting(R.string.preview_waiting_title, WAITING_PROGRESS, TransferState.Waiting),
}

private data class ExampleRow(
    val example: ExampleDownload,
    val state: TransferState = example.initialState,
)

@Composable
internal fun PreviewDownloads(snackbars: SnackbarHostState) {
    val queue = remember { mutableStateListOf(ExampleRow(ExampleDownload.Active), ExampleRow(ExampleDownload.Queued)) }
    val examples =
        remember {
            mutableStateListOf(ExampleRow(ExampleDownload.Paused), ExampleRow(ExampleDownload.Waiting))
        }
    PreviewDownloadGroup(stringResource(R.string.preview_queue), queue, snackbars)
    PreviewDownloadGroup(stringResource(R.string.preview_other_states), examples, snackbars)
}

@Composable
private fun PreviewDownloadGroup(
    title: String,
    rows: MutableList<ExampleRow>,
    snackbars: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val cancelled = stringResource(R.string.download_cancelled)
    val undo = stringResource(R.string.download_undo)
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        for (row in rows) {
            key(row.example) {
                PreviewDownloadRow(
                    row,
                    onToggle = { rows[rows.indexOf(row)] = row.copy(state = row.state.toggled()) },
                    onCancel = {
                        rows.remove(row)
                        scope.launch {
                            val result = snackbars.showSnackbar(cancelled, undo, duration = SnackbarDuration.Short)
                            if (result == SnackbarResult.ActionPerformed) {
                                rows.add(row)
                                rows.sortBy { it.example.ordinal }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PreviewDownloadRow(
    row: ExampleRow,
    onToggle: () -> Unit,
    onCancel: () -> Unit,
) {
    val metrics = if (row.example == ExampleDownload.Active) ActiveMetrics else null
    DownloadRow(
        content = DownloadRowContent(stringResource(row.example.title), row.state, row.example.progress, metrics),
        onToggle = onToggle,
        onCancel = onCancel,
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}

private fun TransferState.toggled(): TransferState =
    if (this == TransferState.Downloading) {
        TransferState.Paused
    } else {
        TransferState.Downloading
    }
