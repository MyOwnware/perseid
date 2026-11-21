package app.myownware.perseid.feature.download

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.myownware.perseid.R
import app.myownware.perseid.core.design.EmptyState

@Composable
internal fun DownloadPlaceholder() {
    EmptyState(
        title = stringResource(R.string.download_empty_title),
        description = stringResource(R.string.download_empty_description),
    )
}
