package app.myownware.perseid.feature.download

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DesignSize
import app.myownware.perseid.core.design.MediaChoice
import app.myownware.perseid.core.design.MediaChoiceControl
import app.myownware.perseid.core.design.PrimaryButton
import app.myownware.perseid.core.engine.MediaOption
import app.myownware.perseid.core.engine.MediaSize
import app.myownware.perseid.core.engine.MediaType
import app.myownware.perseid.core.engine.TrackMerge
import app.myownware.perseid.core.locale.formatFileSize
import app.myownware.perseid.core.locale.formatVideoResolution

@Composable
internal fun MediaSelection(
    preview: PreviewState.Ready?,
    model: DownloadViewModel,
    onDownload: () -> Unit,
) {
    val type = preview?.type ?: MediaType.Video
    if (preview != null) {
        MediaTypePicker(preview, model::selectType)
        Crossfade(type, animationSpec = tween(durationMillis = TYPE_TRANSITION_MILLIS), label = "Media type") { shown ->
            when (shown) {
                MediaType.Video -> {
                    VideoOptions(preview, model::selectVideo)
                }

                MediaType.Audio -> {
                    preview.media.options.audio
                        ?.let { AudioOption(it) }
                }
            }
        }
    }
    val label = if (type == MediaType.Video) R.string.download_video else R.string.download_audio
    PrimaryButton(
        label = stringResource(label),
        enabled = preview?.selectedOption != null,
        onClick = onDownload,
    )
}

@Composable
private fun MediaTypePicker(
    preview: PreviewState.Ready,
    onSelect: (MediaType) -> Unit,
) {
    val options = preview.media.options
    if (options.video.isEmpty() && options.audio == null) return
    val disabledChoice =
        when {
            options.video.isEmpty() -> MediaChoice.Video
            options.audio == null -> MediaChoice.Audio
            else -> null
        }
    MediaChoiceControl(
        choice = if (preview.type == MediaType.Video) MediaChoice.Video else MediaChoice.Audio,
        onChoice = { onSelect(if (it == MediaChoice.Video) MediaType.Video else MediaType.Audio) },
        modifier = Modifier.fillMaxWidth(),
        disabledChoice = disabledChoice,
    )
}

@Composable
private fun VideoOptions(
    preview: PreviewState.Ready,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        preview.media.options.video.forEachIndexed { index, option ->
            val selected = index == preview.videoIndex
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = DesignSize.TouchTarget)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) }),
                horizontalArrangement = Arrangement.spacedBy(DesignSize.RowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null)
                VideoOptionDetails(option)
            }
        }
    }
}

@Composable
private fun VideoOptionDetails(option: MediaOption) {
    val context = LocalContext.current
    val height = option.height
    Column {
        Text(height?.let { formatVideoResolution(context, it) } ?: stringResource(R.string.media_video))
        option.size?.let { SizeText(it) }
        if (option.merge == TrackMerge.WebM) {
            Text(stringResource(R.string.download_compatibility), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AudioOption(option: MediaOption) {
    Column(Modifier.fillMaxWidth().heightIn(min = DesignSize.TouchTarget), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.download_best_audio))
        option.size?.let { SizeText(it) }
    }
}

@Composable
private fun SizeText(size: MediaSize) {
    val formatted = formatFileSize(LocalContext.current, size.bytes)
    val label = if (size.estimated) stringResource(R.string.download_estimated_size, formatted) else formatted
    Text(label, style = MaterialTheme.typography.bodySmall)
}

private const val TYPE_TRANSITION_MILLIS = 200
