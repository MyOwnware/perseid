package app.myownware.perseid.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DesignSize
import app.myownware.perseid.core.design.ErrorMessage
import app.myownware.perseid.core.design.MediaChoice
import app.myownware.perseid.core.design.MediaChoiceControl
import app.myownware.perseid.core.design.PrimaryButton
import app.myownware.perseid.core.design.QualityListItem
import app.myownware.perseid.core.design.SecondaryButton
import app.myownware.perseid.core.locale.formatFileSize
import app.myownware.perseid.core.locale.formatVideoResolution

private const val HIGH_RESOLUTION = 1080
private const val LOW_RESOLUTION = 720
private const val HIGH_SIZE_BYTES = 142_000_000L
private const val LOW_SIZE_BYTES = 76_000_000L

@Composable
internal fun PreviewControls() {
    var choice by remember { mutableStateOf(MediaChoice.Video) }
    Column(verticalArrangement = Arrangement.spacedBy(DesignSize.Small)) {
        MediaChoiceControl(choice, { choice = it }, Modifier.fillMaxWidth())
        PreviewQuality()
        PrimaryButton(stringResource(R.string.preview_download_video), {}, Modifier.fillMaxWidth())
        ErrorMessage(stringResource(R.string.preview_link_error)) {
            SecondaryButton(stringResource(R.string.preview_retry), {})
        }
    }
}

@Composable
private fun PreviewQuality() {
    var high by remember { mutableStateOf(true) }
    val context = LocalContext.current
    Column(Modifier.selectableGroup()) {
        QualityListItem(
            formatVideoResolution(context, HIGH_RESOLUTION),
            formatFileSize(context, HIGH_SIZE_BYTES),
            high,
            { high = true },
        )
        QualityListItem(
            formatVideoResolution(context, LOW_RESOLUTION),
            formatFileSize(context, LOW_SIZE_BYTES),
            !high,
            { high = false },
        )
    }
}
