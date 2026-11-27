package com.intec.immichfire.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.intec.immichfire.data.ApiResult
import com.intec.immichfire.data.ImmichError
import com.intec.immichfire.data.ImmichRepository
import com.intec.immichfire.network.AlbumSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SourcePickerState(
    val loading: Boolean = false,
    val albums: List<AlbumSummary> = emptyList(),
    val folders: List<String> = emptyList(),
    val error: ImmichError? = null,
)

/**
 * Owns the Source Picker's remote state — albums, folder paths, error — so the composable
 * can stay plain UI. Fetch is explicit (refresh()) because the screen triggers it both on
 * entry and on ON_RESUME; folding both into a StateFlow would make the trigger surface
 * larger, not smaller.
 */
class SourcePickerViewModel(private val repo: ImmichRepository) : ViewModel() {

    private val _state = MutableStateFlow(SourcePickerState())
    val state: StateFlow<SourcePickerState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val albumsResult = repo.listAlbums()
            // Folder listing failing shouldn't blank the albums list; keep them independent.
            val foldersResult = repo.listFolderPaths()
            _state.update { prev ->
                val albums = (albumsResult as? ApiResult.Success)?.value ?: prev.albums
                val folders = (foldersResult as? ApiResult.Success)?.value ?: prev.folders
                val firstError = (albumsResult as? ApiResult.Failure)?.error
                    ?: (foldersResult as? ApiResult.Failure)?.error
                prev.copy(
                    loading = false,
                    albums = albums,
                    folders = folders,
                    error = firstError,
                )
            }
        }
    }

    class Factory(private val repo: ImmichRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SourcePickerViewModel(repo) as T
    }
}
