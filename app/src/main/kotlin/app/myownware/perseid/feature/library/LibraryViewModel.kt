package app.myownware.perseid.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.myownware.perseid.R
import app.myownware.perseid.core.download.DownloadJob
import app.myownware.perseid.core.engine.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal class LibraryViewModel(
    private val library: LibraryFiles,
) : ViewModel() {
    private val filters = MutableStateFlow(LibraryFilters())
    private val checked = MutableStateFlow(false)
    val state =
        combine(library.items, filters, checked) { items, criteria, ready ->
            LibraryUiState(items.takeIf { ready }, criteria)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), LibraryUiState())

    fun search(query: String) {
        filters.update { it.copy(search = query) }
    }

    fun filter(choice: LibraryFilter) {
        filters.update { it.copy(filter = choice) }
    }

    suspend fun refresh() {
        library.reconcile()
        checked.value = true
    }

    suspend fun delete(item: DownloadJob): Boolean = library.delete(item)
}

internal data class LibraryUiState(
    val downloads: List<DownloadJob>? = null,
    val filters: LibraryFilters = LibraryFilters(),
)

internal data class LibraryFilters(
    val search: String = "",
    val filter: LibraryFilter = LibraryFilter.All,
)

internal enum class LibraryFilter(
    val label: Int,
    val type: MediaType?,
) {
    All(R.string.library_all, null),
    Video(R.string.media_video, MediaType.Video),
    Audio(R.string.media_audio, MediaType.Audio),
}

internal fun List<DownloadJob>.matching(
    filter: LibraryFilter,
    search: String,
): List<DownloadJob> {
    val query = search.trim()
    return filter { item ->
        (filter.type == null || item.type == filter.type) &&
            (item.title.contains(query, ignoreCase = true) || item.author?.contains(query, ignoreCase = true) == true)
    }
}
