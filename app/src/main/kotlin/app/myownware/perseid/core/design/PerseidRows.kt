package app.myownware.perseid.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

@Composable
internal fun QualityListItem(
    label: String,
    size: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = DesignSize.TouchTarget)
                .selectable(selected, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = DesignSize.Small),
        horizontalArrangement = Arrangement.spacedBy(DesignSize.RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = colors.onSurface))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (size != null) Text(size, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}
