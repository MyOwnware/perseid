package app.myownware.perseid.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.myownware.perseid.R
import app.myownware.perseid.core.design.DesignSize
import app.myownware.perseid.core.design.PerseidTheme
import app.myownware.perseid.core.design.PerseidTopBar

class ComponentPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PerseidTheme { ComponentGallery() }
        }
    }
}

@Composable
private fun ComponentGallery() {
    val snackbars = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = colors.background,
        contentColor = colors.onBackground,
        topBar = { PerseidTopBar(stringResource(R.string.preview_title)) },
        snackbarHost = { PreviewSnackbarHost(snackbars) },
    ) { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignSize.Gutter, vertical = DesignSize.Small),
            verticalArrangement = Arrangement.spacedBy(DesignSize.Section),
        ) {
            PreviewDownloads(snackbars)
            PreviewControls()
        }
    }
}

@Composable
private fun PreviewSnackbarHost(snackbars: SnackbarHostState) {
    val colors = MaterialTheme.colorScheme
    SnackbarHost(snackbars) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = colors.onSurface,
            contentColor = colors.surface,
            actionColor = colors.surface,
        )
    }
}
