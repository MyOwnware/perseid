package app.myownware.perseid.core

import android.content.Context
import app.myownware.perseid.core.engine.PreviewEngine
import app.myownware.perseid.core.engine.YtDlpPreviewEngine

class AppContainer(
    context: Context,
) {
    internal val previewEngine: PreviewEngine = YtDlpPreviewEngine(context.applicationContext)
}
