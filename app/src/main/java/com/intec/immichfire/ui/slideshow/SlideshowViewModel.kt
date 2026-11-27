package com.intec.immichfire.ui.slideshow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.intec.immichfire.data.ApiResult
import com.intec.immichfire.data.ImmichError
import com.intec.immichfire.data.ImmichRepository
import com.intec.immichfire.data.SortOrder
import com.intec.immichfire.network.Asset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SlideshowState(
    val loading: Boolean = true,
    val error: ImmichError? = null,
    val assets: List<Asset> = emptyList(),
    val index: Int = 0,
    val playing: Boolean = true,
    val showInfo: Boolean = false,
)

/**
 * Owns the slideshow queue, playback index, and overlay toggles. Goes through the typed
 * [ImmichRepository] so the screen stays Compose-only.
 */
class SlideshowViewModel(
    private val repo: ImmichRepository,
    private val source: String,
    private val param: String,
) : ViewModel() {

    private val _state = MutableStateFlow(SlideshowState())
    val state: StateFlow<SlideshowState> = _state.asStateFlow()

    private var currentSortOrder: SortOrder = SortOrder.NewestFirst

    fun load(shuffle: Boolean, sortOrder: SortOrder) {
        currentSortOrder = sortOrder
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (source) {
                "album" -> handleListResult(repo.listAlbumAssets(param, sortOrder), shuffle, sortOrder)
                "folder" -> handleListResult(repo.listFolderAssets(param), shuffle, sortOrder)
                else -> loadRecentPaged(shuffle, sortOrder)
            }
        }
    }

    private fun handleListResult(
        result: ApiResult<List<Asset>>,
        shuffle: Boolean,
        sortOrder: SortOrder,
    ) {
        when (result) {
            is ApiResult.Success -> {
                val ordered = applyClientSort(result.value, sortOrder)
                val finalList = if (shuffle) ordered.shuffled() else ordered
                _state.update { it.copy(loading = false, assets = finalList, index = 0) }
            }
            is ApiResult.Failure -> _state.update {
                it.copy(loading = false, error = result.error)
            }
        }
    }

    /**
     * Resort an already-loaded slideshow when the user flips newest-first vs oldest-first from
     * the on-screen controls. Keeps playback going without re-fetching from the server.
     */
    fun resort(sortOrder: SortOrder) {
        if (sortOrder == currentSortOrder) return
        currentSortOrder = sortOrder
        _state.update { s ->
            if (s.assets.isEmpty()) return@update s
            val current = s.assets[s.index]
            val resorted = applyClientSort(s.assets, sortOrder)
            val newIndex = resorted.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
            s.copy(assets = resorted, index = newIndex)
        }
    }

    private fun applyClientSort(list: List<Asset>, sortOrder: SortOrder): List<Asset> {
        val keyed = list.sortedBy { dateKey(it) }
        return if (sortOrder == SortOrder.NewestFirst) keyed.asReversed() else keyed
    }

    private fun dateKey(a: Asset): String =
        a.exifInfo?.dateTimeOriginal ?: a.localDateTime ?: a.fileCreatedAt ?: ""

    private suspend fun loadRecentPaged(shuffle: Boolean, sortOrder: SortOrder) {
        val result = repo.listRecentAssetsPaged(sortOrder) { accumulated ->
            // Only shuffle once at the end (see below). Mid-paging we just append so the
            // currently-playing slide doesn't change under the user.
            _state.update { s ->
                s.copy(
                    loading = false,
                    assets = accumulated,
                    index = s.index.coerceAtMost((accumulated.size - 1).coerceAtLeast(0)),
                )
            }
        }
        if (result is ApiResult.Failure) {
            _state.update { it.copy(loading = false, error = result.error) }
            return
        }
        if (shuffle) {
            _state.update { s ->
                val shuffled = s.assets.shuffled()
                s.copy(assets = shuffled, index = 0)
            }
        }
    }

    fun next(loop: Boolean) {
        _state.update { s ->
            if (s.assets.isEmpty()) return@update s
            val n = s.index + 1
            val newIndex = when {
                n < s.assets.size -> n
                loop -> 0
                else -> s.index
            }
            s.copy(index = newIndex)
        }
    }

    fun prev(loop: Boolean) {
        _state.update { s ->
            if (s.assets.isEmpty()) return@update s
            val n = s.index - 1
            val newIndex = when {
                n >= 0 -> n
                loop -> s.assets.size - 1
                else -> s.index
            }
            s.copy(index = newIndex)
        }
    }

    fun togglePlay() = _state.update { it.copy(playing = !it.playing) }
    fun toggleInfo() = _state.update { it.copy(showInfo = !it.showInfo) }

    class Factory(
        private val repo: ImmichRepository,
        private val source: String,
        private val param: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SlideshowViewModel(repo, source, param) as T
    }
}
