package app.myownware.perseid.core.engine

import android.content.Context
import app.myownware.perseid.BuildConfig
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.time.DateTimeException
import java.time.LocalDate

internal object BundledEngine {
    private var store: EngineStore? = null

    @Synchronized
    fun prepare(context: Context): EngineSelection {
        store?.selection()?.let { return it }
        val executable = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")
        val existing = executable.exists()
        YoutubeDL.init(context)
        val storage = EngineStore(File(context.noBackupFilesDir, "download-engine"), executable)
        val selection = initialize(context, storage, executable, existing)
        store = storage
        return selection
    }

    @Synchronized
    fun activate(
        context: Context,
        version: String,
        candidate: File,
    ): EngineSelection {
        if (Thread.interrupted()) throw InterruptedException()
        val active = prepare(context)
        if (!shouldInstallBundled(active.current, version)) return active
        val storage = requireNotNull(store)
        candidate.inputStream().use { storage.save(version, it) }
        return active.copy(current = version, previous = active.current).also(storage::select)
    }

    @Synchronized
    fun restore(context: Context): EngineSelection {
        if (Thread.interrupted()) throw InterruptedException()
        prepare(context)
        return requireNotNull(store).restore()
    }

    @Synchronized
    fun execute(
        request: YoutubeDLRequest,
        processId: String,
    ) = YoutubeDL.execute(request, processId)

    private fun initialize(
        context: Context,
        storage: EngineStore,
        executable: File,
        existing: Boolean,
    ): EngineSelection {
        val bundled = BuildConfig.BUNDLED_YT_DLP_VERSION
        val selected = storage.selection()
        if (selected != null && selected.bundleSeen == BUNDLE_BUILD) {
            storage.sync(selected)
            return selected
        }
        val current = selected?.current ?: installedVersion()
        if (selected == null && (existing || !shouldInstallBundled(current, bundled))) {
            executable.inputStream().use { storage.save(current, it) }
        }
        val updated = selectBundle(context, storage, selected, current, existing)
        storage.select(updated)
        return updated
    }

    private fun selectBundle(
        context: Context,
        storage: EngineStore,
        selected: EngineSelection?,
        current: String,
        existing: Boolean,
    ): EngineSelection {
        val bundled = BuildConfig.BUNDLED_YT_DLP_VERSION
        if (!shouldInstallBundled(current, bundled)) {
            return EngineSelection(current, selected?.previous, BUNDLE_BUILD)
        }
        context.assets.open("engine/yt-dlp").use { storage.save(bundled, it) }
        return EngineSelection(bundled, current.takeIf { existing || selected != null }, BUNDLE_BUILD)
    }
}

private fun installedVersion(): String {
    val request = YoutubeDLRequest(emptyList<String>()).apply { addOption("--version") }
    return YoutubeDL.execute(request).out.trim()
}

internal fun shouldInstallBundled(
    installed: String,
    bundled: String,
): Boolean = engineDate(installed) < engineDate(bundled)

internal fun isEngineVersion(version: String): Boolean {
    if (!VERSION_PATTERN.matches(version)) return false
    return try {
        engineDate(version)
        true
    } catch (_: DateTimeException) {
        false
    }
}

private fun engineDate(version: String): LocalDate {
    val (year, month, day) = version.split('.')
    return LocalDate.of(year.toInt(), month.toInt(), day.toInt())
}

private val VERSION_PATTERN = Regex("[0-9]{4}\\.[0-9]{1,2}\\.[0-9]{1,2}")

private const val BUNDLE_BUILD = "${BuildConfig.VERSION_CODE}:${BuildConfig.BUNDLED_YT_DLP_VERSION}"
