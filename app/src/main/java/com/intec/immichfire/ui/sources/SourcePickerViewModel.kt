package com.intec.immichfire.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.intec.immichfire.data.ApiResult
import com.intec.immichfire.data.ImmichError
import com.intec.immichfire.data.ImmichRepository
import com.intec.immichfire.network.AlbumSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A folder row in the picker. [path] is the full server path used for navigation;
 * [displayName] is the path with its external-library root stripped; [count] is the
 * number of image assets inside it, null until the background fetch resolves.
 */
data class FolderEntry(
    val path: String,
    val displayName: String,
    val count: Int? = null,
)

data class SourcePickerState(
    val loading: Boolean = false,
    val albums: List<AlbumSummary> = emptyList(),
    val folders: List<FolderEntry> = emptyList(),
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

    // Cancel any outstanding count fetches when the user refreshes again, so late replies
    // from a previous server can't overwrite the new folder list.
    private var countsJob: Job? = null

    fun refresh() {
        countsJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            val albumsDef = async { repo.listAlbums() }
            // Folder listing failing shouldn't blank the albums list; keep them independent.
            val foldersDef = async { repo.listFolderPaths() }
            // Library import paths are advisory: if the call fails we just fall back to
            // stripping the leading slash so folders still render meaningfully.
            val librariesDef = async { repo.listLibraryImportPaths() }

            val albumsResult = albumsDef.await()
            val foldersResult = foldersDef.await()
            val importPaths = (librariesDef.await() as? ApiResult.Success)?.value.orEmpty()

            val folderPaths = (foldersResult as? ApiResult.Success)?.value.orEmpty()
            val entries = folderPaths.map { p ->
                FolderEntry(path = p, displayName = stripLibraryPrefix(p, importPaths))
            }

            _state.update { prev ->
                val albums = (albumsResult as? ApiResult.Success)?.value ?: prev.albums
                val folders = if (foldersResult is ApiResult.Success) entries else prev.folders
                val firstError = (albumsResult as? ApiResult.Failure)?.error
                    ?: (foldersResult as? ApiResult.Failure)?.error
                prev.copy(
                    loading = false,
                    albums = albums,
                    folders = folders,
                    error = firstError,
                )
            }

            if (entries.isNotEmpty()) countsJob = launchFolderCounts(entries)
        }
    }

    /**
     * Fetch per-folder image counts in parallel, patching each one into state as it lands.
     * Using async/awaitAll (instead of a sequential loop) keeps wall-clock low for users
     * with many folders without hammering the server beyond coroutine-default parallelism.
     */
    private fun launchFolderCounts(entries: List<FolderEntry>): Job = viewModelScope.launch {
        coroutineScope {
            entries.map { entry ->
                async {
                    val count = (repo.listFolderAssets(entry.path) as? ApiResult.Success)
                        ?.value?.size
                    if (count != null) {
                        _state.update { s ->
                            val updated = s.folders.map {
                                if (it.path == entry.path) it.copy(count = count) else it
                            }
                            s.copy(folders = updated)
                        }
                    }
                }
            }.awaitAll()
        }
    }

    class Factory(private val repo: ImmichRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SourcePickerViewModel(repo) as T
    }
}

/**
 * Strip the longest matching external-library import path from [path]. Longest-match wins
 * so that nested import paths ("/mnt/usb" and "/mnt/usb/archive") both resolve correctly.
 * Falls back to stripping the leading slash so a path never renders with a dangling prefix.
 */
internal fun stripLibraryPrefix(path: String, importPaths: List<String>): String {
    val normalized = importPaths
        .asSequence()
        .map { it.trimEnd('/') }
        .filter { it.isNotEmpty() }
        .sortedByDescending { it.length }
    for (root in normalized) {
        if (path == root) return path.substringAfterLast('/').ifEmpty { path }
        if (path.startsWith("$root/")) return path.removePrefix("$root/")
    }
    return path.removePrefix("/")
}
