package app.myownware.perseid

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.myownware.perseid.core.design.PerseidTheme
import app.myownware.perseid.core.locale.AppLanguage
import app.myownware.perseid.core.locale.appLanguage
import app.myownware.perseid.core.locale.changeAppLanguage
import app.myownware.perseid.core.locale.localizedContext

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                PerseidShell(appLanguage(this), ::selectLanguage)
            }
        }
    }

    private fun selectLanguage(choice: AppLanguage) {
        if (choice == appLanguage(this)) return
        changeAppLanguage(this, choice)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recreate()
    }
}
