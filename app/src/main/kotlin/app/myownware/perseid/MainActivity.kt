package app.myownware.perseid

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.myownware.perseid.core.design.PerseidTheme
import app.myownware.perseid.core.download.SHOW_DOWNLOAD
import app.myownware.perseid.core.locale.AppLanguage
import app.myownware.perseid.core.locale.appLanguage
import app.myownware.perseid.core.locale.changeAppLanguage
import app.myownware.perseid.core.locale.localizedContext
import app.myownware.perseid.core.preferences.AppTheme
import app.myownware.perseid.feature.download.DownloadViewModel
import app.myownware.perseid.feature.library.LibraryViewModel
import app.myownware.perseid.feature.settings.EngineUpdateViewModel
import app.myownware.perseid.feature.settings.SettingsScreen
import app.myownware.perseid.feature.settings.StorageViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val download by viewModels<DownloadViewModel> {
        viewModelFactory {
            initializer {
                val container = (application as PerseidApp).container
                DownloadViewModel(container.previewEngine, downloads = container.downloads) {
                    container.preferences.state.value
                }
            }
        }
    }

    private val library by viewModels<LibraryViewModel> {
        viewModelFactory {
            initializer { LibraryViewModel((application as PerseidApp).container.library) }
        }
    }

    private val engineUpdate by viewModels<EngineUpdateViewModel> {
        viewModelFactory {
            initializer { EngineUpdateViewModel((application as PerseidApp).container.engineUpdater) }
        }
    }

    private val storage by viewModels<StorageViewModel> {
        viewModelFactory {
            initializer { StorageViewModel((application as PerseidApp).container.downloads.storage) }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) acceptShare(intent)
        WindowCompat.enableEdgeToEdge(window)
        window.isNavigationBarContrastEnforced = true
        val preferences = (application as PerseidApp).container.preferences
        setContent {
            val selected by preferences.state.collectAsState()
            val dark =
                when (selected.theme) {
                    AppTheme.System -> isSystemInDarkTheme()
                    AppTheme.Light -> false
                    AppTheme.Dark -> true
                }
            SideEffect { updateSystemBars(dark) }
            PerseidTheme(dark = dark) {
                PerseidShell(download, library) {
                    SettingsScreen(appLanguage(this), ::selectLanguage, engineUpdate, preferences, storage)
                }
            }
        }
    }

    private fun updateSystemBars(dark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { (application as PerseidApp).container.downloads.reconcile() }
    }

    override fun onStop() {
        super.onStop()
        engineUpdate.close()
        if (!isChangingConfigurations) download.cancelPreview()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptShare(intent)
    }

    private fun acceptShare(intent: Intent) {
        if (intent.action == SHOW_DOWNLOAD) download.share(null)
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        download.share(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty())
    }

    private fun selectLanguage(choice: AppLanguage) {
        if (choice == appLanguage(this)) return
        changeAppLanguage(this, choice)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recreate()
    }
}
