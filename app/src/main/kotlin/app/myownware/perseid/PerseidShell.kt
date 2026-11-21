package app.myownware.perseid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.myownware.perseid.core.design.DesignMotion
import app.myownware.perseid.core.design.DesignSize
import app.myownware.perseid.core.design.PerseidTopBar
import app.myownware.perseid.core.locale.AppLanguage
import app.myownware.perseid.feature.download.DownloadScreen
import app.myownware.perseid.feature.download.DownloadViewModel
import app.myownware.perseid.feature.library.LibraryPlaceholder
import app.myownware.perseid.feature.settings.SettingsPlaceholder

private enum class Destination(
    val title: Int,
    val icon: Int,
) {
    Download(R.string.download_title, R.drawable.ic_download),
    Library(R.string.library_title, R.drawable.ic_video_library),
    Settings(R.string.settings_title, R.drawable.ic_settings),
}

@Composable
internal fun PerseidShell(
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    download: DownloadViewModel,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.Download) }
    val state by download.state.collectAsState()
    var handledShare by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(state.shareRevision) {
        if (state.shareRevision != handledShare) {
            destination = Destination.Download
            handledShare = state.shareRevision
        }
    }
    BackHandler(destination != Destination.Download) { destination = Destination.Download }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = { PerseidTopBar(stringResource(destination.title)) },
        bottomBar = { DestinationBar(destination) { destination = it } },
    ) { insets ->
        Box(
            Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets),
            contentAlignment = Alignment.TopCenter,
        ) {
            DestinationContent(
                destination,
                language,
                onLanguage,
                download,
                onDownload = { destination = Destination.Download },
            )
        }
    }
}

@Composable
private fun DestinationContent(
    destination: Destination,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    download: DownloadViewModel,
    onDownload: () -> Unit,
) {
    Column(
        Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DesignSize.Gutter, vertical = DesignSize.Section),
    ) {
        when (destination) {
            Destination.Download -> DownloadScreen(download)
            Destination.Library -> LibraryPlaceholder(onDownload)
            Destination.Settings -> SettingsPlaceholder(language, onLanguage)
        }
    }
}

@Composable
private fun DestinationBar(
    selected: Destination,
    onSelect: (Destination) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .selectableGroup(),
        ) {
            for (tab in Destination.entries) {
                DestinationTab(tab, tab == selected, { onSelect(tab) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DestinationTab(
    tab: Destination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tint by animateColorAsState(
        if (selected) colors.onSurface else colors.onSurfaceVariant,
        animationSpec = tween(DesignMotion.SELECTION_MILLIS),
        label = "destination selection",
    )
    Column(
        modifier
            .heightIn(min = 64.dp)
            .selectable(selected, role = Role.Tab, onClick = onClick)
            .padding(bottom = DesignSize.Small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesignSize.Tiny),
    ) {
        Box(Modifier.width(28.dp).height(DesignSize.Tiny).background(if (selected) tint else Color.Transparent))
        Icon(
            painterResource(tab.icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(DesignSize.Icon),
        )
        Text(
            stringResource(tab.title),
            color = tint,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
