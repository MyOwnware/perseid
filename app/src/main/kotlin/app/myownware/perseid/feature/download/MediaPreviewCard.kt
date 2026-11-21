package app.myownware.perseid.feature.download

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DesignSize
import app.myownware.perseid.core.engine.MediaPreview
import app.myownware.perseid.core.locale.formatDuration

private const val VIDEO_ASPECT_RATIO = 16f / 9f

@Composable
internal fun MediaPreviewCard(media: MediaPreview) {
    val locale = LocalConfiguration.current.locales[0]
    val duration = media.durationSeconds?.let { formatDuration(locale, it) }
    val separator = stringResource(R.string.preview_metadata_separator)
    val details = listOfNotNull(media.author, duration).joinToString(separator)
    Column(verticalArrangement = Arrangement.spacedBy(DesignSize.Small)) {
        PreviewImage(media)
        Text(media.title, style = MaterialTheme.typography.titleMedium)
        if (details.isNotEmpty()) Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PreviewImage(media: MediaPreview) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(VIDEO_ASPECT_RATIO)
            .clip(MaterialTheme.shapes.small)
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = media.thumbnail
        if (bitmap == null) {
            Icon(painterResource(R.drawable.ic_movie), contentDescription = null, tint = colors.onSurfaceVariant)
        } else {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
