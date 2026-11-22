package app.myownware.perseid.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.myownware.perseid.R
import app.myownware.perseid.core.locale.TransferMetrics
import app.myownware.perseid.core.locale.formatPercent
import app.myownware.perseid.core.locale.formatTransferDetails

private const val THUMBNAIL_ASPECT_RATIO = 16f / 9f

internal enum class TransferState(
    val status: Int?,
) {
    Downloading(null),
    Paused(R.string.download_paused),
    Waiting(R.string.download_waiting),
    AndroidPaused(R.string.download_android_paused),
    Interrupted(R.string.download_interrupted),
    Retrying(R.string.download_retrying),
    Queued(R.string.download_queued),
    Publishing(R.string.download_publishing),
    Merging(R.string.download_merging),
    Cancelling(R.string.download_cancelling),
}

internal data class DownloadRowContent(
    val title: String,
    val state: TransferState,
    val progress: Float = 0f,
    val metrics: TransferMetrics? = null,
    val thumbnail: Painter? = null,
    val toggleEnabled: Boolean = true,
    val cancelEnabled: Boolean = true,
)

@Composable
internal fun DownloadRow(
    content: DownloadRowContent,
    onToggle: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = DesignSize.Small),
        horizontalArrangement = Arrangement.spacedBy(DesignSize.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DownloadThumbnail(content.thumbnail)
        DownloadRowText(content, Modifier.weight(1f))
        DownloadRowActions(content, onToggle, onCancel)
    }
}

@Composable
private fun DownloadThumbnail(thumbnail: Painter?) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .width(64.dp)
            .aspectRatio(THUMBNAIL_ASPECT_RATIO)
            .clip(MaterialTheme.shapes.small)
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail == null) {
            Icon(painterResource(R.drawable.ic_movie), contentDescription = null, tint = colors.onSurfaceVariant)
        } else {
            Image(
                thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun DownloadRowText(
    content: DownloadRowContent,
    modifier: Modifier,
) {
    val progress = content.progress.coerceIn(0f, 1f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DesignSize.Tiny)) {
        Text(
            content.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (content.state != TransferState.Queued) TransferProgress(progress)
        Text(
            transferStatus(content, progress),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun transferStatus(
    content: DownloadRowContent,
    progress: Float,
): String {
    content.state.status?.let { return stringResource(it) }
    val context = LocalContext.current
    val percentage = formatPercent(progress, LocalConfiguration.current.locales[0])
    val details = content.metrics?.let { formatTransferDetails(context, it) }
    return if (details == null) percentage else stringResource(R.string.download_progress_details, percentage, details)
}

@Composable
private fun DownloadRowActions(
    content: DownloadRowContent,
    onToggle: () -> Unit,
    onCancel: () -> Unit,
) {
    val state = content.state
    Row {
        if (state != TransferState.Queued && state != TransferState.AndroidPaused) {
            val downloading = state != TransferState.Paused
            DownloadIconButton(
                icon = if (downloading) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                description = if (downloading) R.string.download_pause_action else R.string.download_resume_action,
                onClick = onToggle,
                enabled = content.toggleEnabled,
            )
        }
        DownloadIconButton(R.drawable.ic_close, R.string.download_cancel_action, onCancel, content.cancelEnabled)
    }
}

@Composable
private fun DownloadIconButton(
    icon: Int,
    description: Int,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val label = stringResource(description)
    IconButton(
        onClick,
        enabled = enabled,
        modifier = Modifier.size(DesignSize.TouchTarget).semantics { contentDescription = label },
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
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
