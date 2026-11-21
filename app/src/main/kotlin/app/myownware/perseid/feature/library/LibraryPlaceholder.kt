package app.myownware.perseid.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.myownware.perseid.R
import app.myownware.perseid.core.design.EmptyState
import app.myownware.perseid.core.design.SecondaryButton

@Composable
internal fun LibraryPlaceholder(onDownload: () -> Unit) {
    EmptyState(
        title = stringResource(R.string.library_empty_title),
        description = stringResource(R.string.library_empty_description),
    ) {
        SecondaryButton(stringResource(R.string.library_open_download), onClick = onDownload)
    }
}
