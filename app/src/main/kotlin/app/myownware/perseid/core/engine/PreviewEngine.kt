package app.myownware.perseid.core.engine

import android.graphics.Bitmap

internal interface PreviewEngine {
    suspend fun prepare()

    suspend fun preview(url: String): MediaPreview
}

internal data class MediaPreview(
    val title: String,
    val author: String? = null,
    val durationSeconds: Long? = null,
    val thumbnail: Bitmap? = null,
)

internal enum class PreviewError {
    Offline,
    Unsupported,
    SignIn,
    NotFound,
    Unknown,
}

internal class PreviewFailure(
    val error: PreviewError,
    cause: Throwable? = null,
) : Exception(cause)
