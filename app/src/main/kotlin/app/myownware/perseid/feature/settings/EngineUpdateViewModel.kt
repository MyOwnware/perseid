package app.myownware.perseid.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.myownware.perseid.BuildConfig
import app.myownware.perseid.core.engine.EngineProblem
import app.myownware.perseid.core.engine.EngineSelection
import app.myownware.perseid.core.engine.EngineUpdateFailure
import app.myownware.perseid.core.engine.EngineUpdater
import app.myownware.perseid.core.engine.shouldInstallBundled
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.coroutines.coroutineContext

internal enum class EngineStatus { Idle, Checking, Available, UpToDate, Updating, Waiting, Updated, Restored, Failed }

internal data class EngineUpdateState(
    val installed: String = BuildConfig.BUNDLED_YT_DLP_VERSION,
    val previous: String? = null,
    val available: String? = null,
    val status: EngineStatus = EngineStatus.Idle,
    val problem: EngineProblem? = null,
) {
    val busy get() = status in setOf(EngineStatus.Checking, EngineStatus.Updating, EngineStatus.Waiting)
}

internal class EngineUpdateViewModel(
    private val engine: EngineUpdater,
) : ViewModel() {
    private val mutableState = MutableStateFlow(EngineUpdateState())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null

    fun refresh() =
        runOperation(EngineProblem.Storage) {
            showInstalled(engine.installed())
        }

    fun check() {
        if (state.value.status == EngineStatus.Idle) operation?.cancel()
        runOperation(EngineProblem.Network, EngineStatus.Checking) {
            val active = engine.installed()
            showInstalled(active)
            val available = engine.available()
            val newer = shouldInstallBundled(active.current, available)
            val status = if (newer) EngineStatus.Available else EngineStatus.UpToDate
            mutableState.update { it.copy(available = available, status = status) }
        }
    }

    fun confirm() {
        val choice = state.value.takeIf { it.status == EngineStatus.Available }?.available ?: return
        runOperation(EngineProblem.Network, EngineStatus.Updating) {
            showInstalled(engine.update(choice, ::waitForIdle))
            mutableState.update { it.copy(status = EngineStatus.Updated) }
        }
    }

    fun restore() =
        runOperation(EngineProblem.Storage, EngineStatus.Updating) {
            showInstalled(engine.restore(::waitForIdle))
            mutableState.update { it.copy(status = EngineStatus.Restored) }
        }

    fun close() {
        operation?.cancel()
        operation = null
        mutableState.update { it.copy(status = EngineStatus.Idle, problem = null, available = null) }
    }

    private fun waitForIdle() {
        mutableState.update { it.copy(status = EngineStatus.Waiting) }
    }

    private fun showInstalled(selection: EngineSelection) {
        mutableState.update { it.copy(installed = selection.current, previous = selection.previous) }
    }

    private fun runOperation(
        fallback: EngineProblem,
        status: EngineStatus = EngineStatus.Idle,
        action: suspend () -> Unit,
    ) {
        if (operation?.isActive == true) return
        mutableState.update { it.copy(status = status, problem = null) }
        operation =
            viewModelScope.launch {
                try {
                    action()
                } catch (failure: EngineUpdateFailure) {
                    failed(failure.problem)
                } catch (_: IOException) {
                    failed(fallback)
                } catch (_: YoutubeDLException) {
                    failed(EngineProblem.Storage)
                }
            }
    }

    private suspend fun failed(problem: EngineProblem) {
        coroutineContext.ensureActive()
        mutableState.update { it.copy(status = EngineStatus.Failed, problem = problem) }
    }
}
