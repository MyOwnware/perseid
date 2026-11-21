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
import androidx.compose.ui.res.stringResource
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DesignSize
import app.myownware.perseid.core.design.ErrorMessage
import app.myownware.perseid.core.design.MediaChoice
import app.myownware.perseid.core.design.MediaChoiceControl
import app.myownware.perseid.core.design.PrimaryButton
import app.myownware.perseid.core.design.QualityListItem
import app.myownware.perseid.core.design.SecondaryButton

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
    Column(Modifier.selectableGroup()) {
        QualityListItem(
            stringResource(R.string.preview_quality_high),
            stringResource(R.string.preview_quality_high_size),
            high,
            { high = true },
        )
        QualityListItem(
            stringResource(R.string.preview_quality_low),
            stringResource(R.string.preview_quality_low_size),
            !high,
            { high = false },
        )
    }
}
