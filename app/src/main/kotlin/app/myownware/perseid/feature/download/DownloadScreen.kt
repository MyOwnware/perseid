package app.myownware.perseid.feature.download

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DesignSize
import app.myownware.perseid.core.design.ErrorMessage
import app.myownware.perseid.core.design.SecondaryButton
import app.myownware.perseid.core.engine.PreviewError

@Composable
internal fun DownloadScreen(
    model: DownloadViewModel,
    snackbars: SnackbarHostState,
) {
    val state by model.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(DesignSize.Section)) {
        LinkInput(state.link, model)
        PreviewContent(state.preview, model::requestPreview)
        MediaSelection(
            state.preview as? PreviewState.Ready,
            model,
            downloadAction { model.controls.start(model.state.value) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        DownloadStatus(state.queue, model.controls, snackbars)
    }
}

@Composable
private fun LinkInput(
    link: String,
    model: DownloadViewModel,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val preview = {
        focus.clearFocus()
        model.requestPreview()
    }
    Column(verticalArrangement = Arrangement.spacedBy(DesignSize.Small)) {
        OutlinedTextField(
            value = link,
            onValueChange = model::editLink,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.preview_link_label)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.onSurface,
                    focusedLabelColor = colors.onSurface,
                ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { preview() }),
            trailingIcon = {
                TextButton(
                    onClick = {
                        focus.clearFocus()
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        model.paste(
                            clipboard
                                ?.primaryClip
                                ?.getItemAt(0)
                                ?.text
                                ?.toString()
                                .orEmpty(),
                        )
                    },
                    modifier = Modifier.heightIn(min = DesignSize.TouchTarget),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant),
                ) { Text(stringResource(R.string.preview_paste)) }
            },
        )
        SecondaryButton(stringResource(R.string.preview_action), preview)
    }
}

@Composable
private fun PreviewContent(
    state: PreviewState,
    onRetry: () -> Unit,
) {
    when (state) {
        PreviewState.Empty -> {}

        PreviewState.Invalid -> {
            ErrorMessage(stringResource(R.string.preview_invalid)) {}
        }

        PreviewState.Preparing -> {
            PreviewLoading(R.string.preview_preparing)
        }

        PreviewState.Loading -> {
            PreviewLoading(R.string.preview_loading)
        }

        is PreviewState.Ready -> {
            MediaPreviewCard(state.media)
        }

        is PreviewState.Failed -> {
            PreviewProblem(state.error, onRetry)
        }
    }
}

@Composable
private fun PreviewLoading(label: Int) {
    Row(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(DesignSize.RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(DesignSize.Icon),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            strokeWidth = 2.dp,
        )
        Text(stringResource(label))
    }
}

@Composable
private fun PreviewProblem(
    error: PreviewError,
    onRetry: () -> Unit,
) {
    val text =
        when (error) {
            PreviewError.Offline -> R.string.preview_offline
            PreviewError.Unsupported -> R.string.preview_unsupported
            PreviewError.SignIn -> R.string.preview_sign_in
            PreviewError.InstagramSignIn -> R.string.preview_instagram_sign_in
            PreviewError.NotFound -> R.string.preview_not_found
            PreviewError.Unknown -> R.string.preview_unknown
        }
    ErrorMessage(stringResource(text)) {
        if (error == PreviewError.Offline || error == PreviewError.Unknown) {
            SecondaryButton(stringResource(R.string.preview_try_again), onRetry)
        }
    }
}
