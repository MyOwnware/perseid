package app.myownware.perseid.feature.library

import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DesignSize
import app.myownware.perseid.core.download.DownloadJob
import app.myownware.perseid.core.engine.MediaType
import app.myownware.perseid.core.locale.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun LibraryRow(
    item: DownloadJob,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = DesignSize.TouchTarget)
                .clickable(onClickLabel = stringResource(R.string.download_open), onClick = onOpen)
                .padding(top = DesignSize.Small),
            horizontalArrangement = Arrangement.spacedBy(DesignSize.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryThumbnail(item.thumbnailPath)
            Column {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                item.author?.takeIf(String::isNotBlank)?.let { Text(it) }
                LibraryMetadata(item)
            }
        }
        val actions = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onShare, colors = actions) { Text(stringResource(R.string.download_share)) }
            TextButton(onClick = onDelete, colors = actions) { Text(stringResource(R.string.library_delete)) }
        }
    }
}

@Composable
private fun LibraryThumbnail(path: String?) {
    val thumbnail by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    }
    Box(
        Modifier
            .size(width = 80.dp, height = 56.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        val image = thumbnail
        if (image == null) {
            Icon(painterResource(R.drawable.ic_movie), contentDescription = null)
        } else {
            Image(image, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun LibraryMetadata(item: DownloadJob) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val type = stringResource(if (item.type == MediaType.Video) R.string.media_video else R.string.media_audio)
    val duration = item.durationSeconds?.let { formatDuration(locale, it) }
    val size = item.sizeBytes?.let { Formatter.formatShortFileSize(context, it) }
    val date = item.finishedAt?.let { DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(it)) }
    val separator = stringResource(R.string.preview_metadata_separator)
    Text(
        listOfNotNull(type, duration, size, date).joinToString(separator),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
