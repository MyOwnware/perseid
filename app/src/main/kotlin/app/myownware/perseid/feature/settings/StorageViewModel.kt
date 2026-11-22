package app.myownware.perseid.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.myownware.perseid.core.download.UnfinishedStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.UncheckedIOException

internal data class StorageState(
    val bytes: Long? = null,
    val clearing: Boolean = false,
    val failed: Boolean = false,
)

internal class StorageViewModel(
    private val storage: UnfinishedStorage,
) : ViewModel() {
    private val values = MutableStateFlow(StorageState())
    val state = values.asStateFlow()

    fun refresh() = update(clear = false)

    fun clear() = update(clear = true)

    private fun update(clear: Boolean) {
        if (values.value.clearing) return
        values.value = values.value.copy(clearing = clear, failed = false)
        viewModelScope.launch {
            values.value =
                try {
                    if (clear) storage.clear()
                    StorageState(bytes = storage.size())
                } catch (_: IOException) {
                    values.value.copy(clearing = false, failed = true)
                } catch (_: UncheckedIOException) {
                    values.value.copy(clearing = false, failed = true)
                }
        }
    }
}
