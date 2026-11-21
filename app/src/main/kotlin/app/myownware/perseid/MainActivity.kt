package app.myownware.perseid

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.myownware.perseid.core.design.PerseidTheme

class MainActivity : ComponentActivity() {
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
                PerseidShell()
            }
        }
    }
}
