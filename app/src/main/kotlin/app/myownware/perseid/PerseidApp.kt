package app.myownware.perseid

import android.app.Application
import app.myownware.perseid.core.AppContainer

class PerseidApp : Application() {
    val container by lazy { AppContainer(this) }
}
