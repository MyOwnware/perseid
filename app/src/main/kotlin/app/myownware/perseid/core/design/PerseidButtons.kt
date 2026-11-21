package app.myownware.perseid.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.myownware.perseid.R

@Composable
internal fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = DesignSize.TouchTarget),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(label)
    }
}

@Composable
internal fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = DesignSize.TouchTarget),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
    ) {
        Text(label)
    }
}

internal enum class MediaChoice(
    val label: Int,
) {
    Video(R.string.media_video),
    Audio(R.string.media_audio),
}

@Composable
internal fun MediaChoiceControl(
    choice: MediaChoice,
    onChoice: (MediaChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    val outline = MaterialTheme.colorScheme.outline
    Row(modifier.clip(shape).border(1.dp, outline, shape).selectableGroup()) {
        for (option in MediaChoice.entries) {
            MediaChoiceSegment(
                option = option,
                selected = choice == option,
                onClick = { onChoice(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MediaChoiceSegment(
    option: MediaChoice,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (selected) colors.onSurface else colors.background,
        contentColor = if (selected) colors.background else colors.onSurface,
        modifier = modifier.selectable(selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Box(
            modifier = Modifier.heightIn(min = DesignSize.TouchTarget).padding(DesignSize.Small),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(option.label), style = MaterialTheme.typography.labelLarge)
        }
    }
}
