package app.myownware.perseid.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.myownware.perseid.R
import kotlin.math.roundToInt

private const val PERCENT_SCALE = 100

@Composable
internal fun QualityListItem(
    label: String,
    size: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = DesignSize.TouchTarget)
                .selectable(selected, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = DesignSize.Small),
        horizontalArrangement = Arrangement.spacedBy(DesignSize.RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = colors.onSurface))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (size != null) Text(size, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

internal enum class TransferState(
    val status: Int?,
) {
    Downloading(null),
    Paused(R.string.download_paused),
    Waiting(R.string.download_waiting),
}

@Composable
internal fun DownloadProgressRow(
    title: String,
    progress: Float,
    state: TransferState,
    details: String?,
    modifier: Modifier = Modifier,
) {
    val progressValue = progress.coerceIn(0f, 1f)
    val label = state.status?.let { stringResource(it) } ?: details
    val percent = stringResource(R.string.download_progress_percent, (progressValue * PERCENT_SCALE).roundToInt())
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DesignSize.Small)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        TransferProgress(progressValue)
        Text(
            text =
                if (label == null) percent else stringResource(R.string.download_progress_details, percent, label),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TransferProgress(progress: Float) {
    val fill by animateFloatAsState(
        progress,
        animationSpec = tween(DesignMotion.STATE_MILLIS),
        label = "transfer progress",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(DesignSize.Tiny)
            .progressSemantics(progress)
            .background(progressTrackColor()),
    ) {
        Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
    }
}
