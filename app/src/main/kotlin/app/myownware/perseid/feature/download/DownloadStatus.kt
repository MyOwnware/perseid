package app.myownware.perseid.feature.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DesignSize
import app.myownware.perseid.core.design.DownloadRow
import app.myownware.perseid.core.design.DownloadRowContent
import app.myownware.perseid.core.design.EmptyState
import app.myownware.perseid.core.design.ErrorMessage
import app.myownware.perseid.core.design.SecondaryButton
import app.myownware.perseid.core.design.TransferState
import app.myownware.perseid.core.download.DownloadError
import app.myownware.perseid.core.download.DownloadJob
import app.myownware.perseid.core.download.JobState
import app.myownware.perseid.core.download.MAX_PERCENT
import app.myownware.perseid.core.download.SavedMedia
import app.myownware.perseid.core.download.launchSavedMedia
import app.myownware.perseid.core.locale.TransferMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DownloadStatus(
    job: DownloadJob?,
    controls: DownloadControls,
    snackbars: SnackbarHostState,
) {
    val requester = remember { BringIntoViewRequester() }
    val pending by controls.pending.collectAsState()
    val message = stringResource(R.string.download_cancelling)
    val undo = stringResource(R.string.download_undo)
    LaunchedEffect(pending) {
        if (pending != null) {
            val result = snackbars.showSnackbar(message, undo, duration = SnackbarDuration.Indefinite)
            if (result == SnackbarResult.ActionPerformed) controls.undoCancel()
        }
    }
    LaunchedEffect(job?.id, job?.state) {
        if (job != null && job.state != JobState.Cancelled) requester.bringIntoView()
    }
    Column(Modifier.bringIntoViewRequester(requester), verticalArrangement = Arrangement.spacedBy(DesignSize.Small)) {
        when {
            job == null || job.state == JobState.Cancelled -> {
                EmptyState(
                    stringResource(R.string.settings_downloads),
                    stringResource(R.string.library_empty_title),
                )
            }

            job.state == JobState.Saved -> {
                SavedDownload(job, snackbars)
            }

            job.state == JobState.Failed -> {
                Text(job.title)
                ErrorMessage(stringResource(job.error.message())) {}
            }

            else -> {
                ActiveDownload(job, pending == job.id) { controls.requestCancel(job) }
            }
        }
    }
}

@Composable
private fun ActiveDownload(
    job: DownloadJob,
    cancelling: Boolean,
    onCancel: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(null, job.thumbnailPath) {
        value = withContext(Dispatchers.IO) { job.thumbnailPath?.let(BitmapFactory::decodeFile) }
    }
    val thumbnail = remember(bitmap) { bitmap?.let { BitmapPainter(it.asImageBitmap()) } }
    val metrics = job.speed?.let { speed -> job.eta?.let { TransferMetrics(speed, it) } }
    val status = if (cancelling) TransferState.Cancelling else job.state.transferState()
    DownloadRow(
        content =
            DownloadRowContent(
                job.title,
                status,
                job.progress.toFloat() / MAX_PERCENT,
                metrics,
                thumbnail,
                toggleEnabled = false,
                cancelEnabled = job.canCancel && !cancelling,
            ),
        onToggle = {},
        onCancel = onCancel,
    )
}

@Composable
private fun SavedDownload(
    job: DownloadJob,
    snackbars: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val media = SavedMedia(requireNotNull(job.contentUri).toUri(), requireNotNull(job.mimeType))
    val unavailable = stringResource(R.string.download_open_failed)
    val open = { share: Boolean ->
        if (!launchSavedMedia(context, media, share)) scope.launch { snackbars.showSnackbar(unavailable) }
    }
    Text(stringResource(R.string.download_saved))
    Text(job.title)
    Row(horizontalArrangement = Arrangement.spacedBy(DesignSize.Small)) {
        SecondaryButton(stringResource(R.string.download_open), { open(false) })
        SecondaryButton(stringResource(R.string.download_share), { open(true) })
    }
}

private fun JobState.transferState(): TransferState =
    when (this) {
        JobState.Downloading -> TransferState.Downloading
        JobState.Publishing -> TransferState.Publishing
        JobState.Paused -> TransferState.Paused
        JobState.Waiting -> TransferState.Interrupted
        JobState.Cancelling -> TransferState.Cancelling
        else -> TransferState.Queued
    }

private fun DownloadError?.message(): Int =
    when (this) {
        DownloadError.NoSpace -> R.string.download_no_space
        DownloadError.Offline -> R.string.download_offline
        DownloadError.Refused -> R.string.download_refused
        DownloadError.Merge -> R.string.download_merge_failed
        DownloadError.Save -> R.string.download_save_failed
        else -> R.string.download_failed
    }
