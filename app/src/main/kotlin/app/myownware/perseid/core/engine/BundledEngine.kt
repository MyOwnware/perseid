package app.myownware.perseid.core.engine

import android.content.Context
import app.myownware.perseid.BuildConfig
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

internal object BundledEngine {
    private var prepared = false

    @Synchronized
    fun prepare(context: Context) {
        if (prepared) return
        YoutubeDL.init(context)
        val versionRequest = YoutubeDLRequest(emptyList<String>()).apply { addOption("--version") }
        val installedVersion = YoutubeDL.execute(versionRequest).out.trim()
        if (shouldInstallBundled(installedVersion, BuildConfig.BUNDLED_YT_DLP_VERSION)) {
            val installed = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")
            installBundled(context, installed)
        }
        prepared = true
    }
}

private fun installBundled(
    context: Context,
    installed: File,
) {
    val pending = File.createTempFile("yt-dlp-", ".tmp", installed.parentFile)
    try {
        context.assets.open("engine/yt-dlp").use { source ->
            pending.outputStream().use { source.copyTo(it) }
        }
        Files.move(
            pending.toPath(),
            installed.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } finally {
        pending.delete()
    }
}

internal fun shouldInstallBundled(
    installed: String,
    bundled: String,
): Boolean = engineDate(installed) < engineDate(bundled)

private fun engineDate(version: String): LocalDate {
    val date = version.split('.')
    val (year, month, day) = date
    return LocalDate.of(year.toInt(), month.toInt(), day.toInt())
}
