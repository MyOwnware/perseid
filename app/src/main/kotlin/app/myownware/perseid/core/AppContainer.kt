package app.myownware.perseid.core

import android.content.Context
import androidx.room.Room
import app.myownware.perseid.core.download.DownloadCoordinator
import app.myownware.perseid.core.download.DownloadDatabase
import app.myownware.perseid.core.download.YtDlpTransferEngine
import app.myownware.perseid.core.engine.EngineUpdater
import app.myownware.perseid.core.engine.PreviewEngine
import app.myownware.perseid.core.engine.YtDlpPreviewEngine
import app.myownware.perseid.core.locale.LANGUAGE_PREFERENCES
import app.myownware.perseid.core.preferences.AppPreferences
import app.myownware.perseid.feature.library.LibraryFiles

class AppContainer(
    context: Context,
) {
    private val database by lazy {
        Room.databaseBuilder(context.applicationContext, DownloadDatabase::class.java, "perseid.db").build()
    }
    internal val preferences by lazy {
        AppPreferences(context.getSharedPreferences(LANGUAGE_PREFERENCES, Context.MODE_PRIVATE))
    }
    internal val library by lazy { LibraryFiles(database.library(), context.contentResolver) }
    internal val downloadDao get() = database.downloads()
    internal val downloads by lazy { DownloadCoordinator(context.applicationContext, database) }
    internal val engineUpdater by lazy { EngineUpdater(context.applicationContext, downloads.execution) }
    internal val transferEngine by lazy { YtDlpTransferEngine(context.applicationContext) }
    internal val previewEngine: PreviewEngine = YtDlpPreviewEngine(context.applicationContext)
}
