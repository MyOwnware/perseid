package app.myownware.perseid

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.myownware.perseid.core.design.PerseidTheme
import app.myownware.perseid.core.download.SHOW_DOWNLOAD
import app.myownware.perseid.core.locale.AppLanguage
import app.myownware.perseid.core.locale.appLanguage
import app.myownware.perseid.core.locale.changeAppLanguage
import app.myownware.perseid.core.locale.localizedContext
import app.myownware.perseid.feature.download.DownloadViewModel
import app.myownware.perseid.feature.settings.EngineUpdateViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val download by viewModels<DownloadViewModel> {
        viewModelFactory {
            initializer {
                val container = (application as PerseidApp).container
                DownloadViewModel(container.previewEngine, downloads = container.downloads)
            }
        }
    }

    private val engineUpdate by viewModels<EngineUpdateViewModel> {
        viewModelFactory {
            initializer { EngineUpdateViewModel((application as PerseidApp).container.engineUpdater) }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) acceptShare(intent)
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val dark = nightMode == Configuration.UI_MODE_NIGHT_YES
        val bars =
            if (dark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            }
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            PerseidTheme(dark = dark) {
                PerseidShell(appLanguage(this), ::selectLanguage, download, engineUpdate)
            }
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
