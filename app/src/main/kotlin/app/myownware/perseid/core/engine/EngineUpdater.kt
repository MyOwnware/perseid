package app.myownware.perseid.core.engine

import android.content.Context
import app.myownware.perseid.core.download.hasConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

internal class EngineUpdater(
    private val context: Context,
    private val execution: Mutex,
) {
    private val remote by lazy {
        EngineRelease(context.assets.open("engine/yt-dlp-public.key").use { it.readBytes() })
    }

    suspend fun installed(): EngineSelection = runInterruptible(Dispatchers.IO) { BundledEngine.prepare(context) }

    suspend fun available(): String = network { remote.latest() }

    suspend fun update(
        version: String,
        waiting: () -> Unit,
    ): EngineSelection =
        withContext(Dispatchers.IO) {
            val file = File.createTempFile("engine-", ".tmp", context.cacheDir)
            try {
                network { remote.download(version, file) }
                whenIdle(execution, waiting) {
                    runInterruptible { BundledEngine.activate(context, version, file) }
                }
            } finally {
                file.delete()
            }
        }

    suspend fun restore(waiting: () -> Unit): EngineSelection =
        withContext(Dispatchers.IO) {
            whenIdle(execution, waiting) { runInterruptible { BundledEngine.restore(context) } }
        }

    private suspend fun <T> network(block: () -> T): T =
        runInterruptible(Dispatchers.IO) {
            if (!context.hasConnection()) throw EngineUpdateFailure(EngineProblem.Offline)
            try {
                block()
            } catch (failure: java.io.IOException) {
                if (!context.hasConnection()) throw EngineUpdateFailure(EngineProblem.Offline)
                throw failure
            }
        }
}

internal suspend fun <T> whenIdle(
    execution: Mutex,
    waiting: () -> Unit,
    action: suspend () -> T,
): T {
    if (execution.isLocked) waiting()
    return execution.withLock {
        coroutineContext.ensureActive()
        action()
    }
}
