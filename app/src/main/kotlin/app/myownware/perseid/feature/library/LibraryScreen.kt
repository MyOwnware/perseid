package app.myownware.perseid.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DesignSize
import app.myownware.perseid.core.design.EmptyState
import app.myownware.perseid.core.design.SecondaryButton
import app.myownware.perseid.core.download.DownloadJob
import app.myownware.perseid.core.download.launchSavedMedia
import kotlinx.coroutines.launch

@Composable
internal fun LibraryScreen(
    viewModel: LibraryViewModel,
    snackbars: SnackbarHostState,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    LifecycleResumeEffect(viewModel) {
        val refresh = scope.launch { viewModel.refresh() }
        onPauseOrDispose { refresh.cancel() }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DesignSize.Small)) {
        val downloads = state.downloads
        when {
            downloads == null -> Unit
            downloads.isEmpty() -> LibraryEmpty(onDownload)
            else -> LibraryResults(downloads, state.filter, viewModel, snackbars)
        }
    }
}

@Composable
private fun LibraryResults(
    downloads: List<DownloadJob>,
    filter: LibraryFilter,
    viewModel: LibraryViewModel,
    snackbars: SnackbarHostState,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val deleteFailure = stringResource(R.string.library_delete_failed)
    val openFailure = stringResource(R.string.download_open_failed)
    val selected = downloads.firstOrNull { it.id == selectedId }

    fun launchItem(
        item: DownloadJob,
        share: Boolean,
    ) {
        val media = item.libraryMedia()
        if (media == null || !launchSavedMedia(context, media, share)) {
            scope.launch { snackbars.showSnackbar(openFailure) }
        }
    }

    LibrarySearch(search, { search = it }, filter, viewModel::filter)
    val visible = downloads.matching(filter, search)
    LazyColumn {
        if (visible.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.library_no_results),
                    stringResource(R.string.library_no_results_description),
                )
            }
        }
        items(visible, key = DownloadJob::id) { item ->
            LibraryRow(
                item,
                onOpen = { launchItem(item, share = false) },
                onShare = { launchItem(item, share = true) },
                onDelete = { selectedId = item.id },
            )
            HorizontalDivider()
        }
    }
    if (selected != null) {
        ConfirmLibraryDelete(selected.title, onCancel = { selectedId = null }) {
            selectedId = null
            scope.launch {
                if (!viewModel.delete(selected)) snackbars.showSnackbar(deleteFailure)
            }
        }
    }
}

@Composable
private fun LibrarySearch(
    search: String,
    onSearch: (String) -> Unit,
    filter: LibraryFilter,
    onFilter: (LibraryFilter) -> Unit,
) {
    OutlinedTextField(
        search,
        onSearch,
        Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.library_search)) },
        singleLine = true,
    )
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(DesignSize.Small)) {
        for (option in LibraryFilter.entries) {
            FilterChip(
                selected = filter == option,
                onClick = { onFilter(option) },
                label = { Text(stringResource(option.label)) },
                modifier = Modifier.heightIn(min = DesignSize.TouchTarget),
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        }
    }
}

@Composable
private fun ConfirmLibraryDelete(
    title: String,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.library_delete_title)) },
        text = { Text(stringResource(R.string.library_delete_message, title)) },
        confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.library_delete)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.download_cancel_restart)) } },
    )
}

@Composable
private fun LibraryEmpty(onDownload: () -> Unit) {
    EmptyState(
        title = stringResource(R.string.library_empty_title),
        description = stringResource(R.string.library_empty_description),
    ) {
        SecondaryButton(stringResource(R.string.library_open_download), onClick = onDownload)
    }
}
