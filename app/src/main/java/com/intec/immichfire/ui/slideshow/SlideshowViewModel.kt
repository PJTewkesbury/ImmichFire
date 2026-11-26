package com.intec.immichfire.ui.slideshow

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.intec.immichfire.data.SortOrder
import com.intec.immichfire.network.Asset
import com.intec.immichfire.network.ImmichApi
import com.intec.immichfire.network.ImmichClient
import com.intec.immichfire.network.SearchMetadataRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SlideshowState(
    val loading: Boolean = true,
    val error: String? = null,
    val assets: List<Asset> = emptyList(),
    val index: Int = 0,
    val playing: Boolean = true,
    val showInfo: Boolean = false,
)

class SlideshowViewModel(
    private val appContext: Context,
    private val source: String,
    private val param: String,
) : ViewModel() {

    private val _state = MutableStateFlow(SlideshowState())
    val state: StateFlow<SlideshowState> = _state.asStateFlow()

    private var loaded = false
    private var currentSortOrder: SortOrder = SortOrder.NewestFirst

    fun load(serverUrl: String, apiKey: String, shuffle: Boolean, sortOrder: SortOrder) {
        if (loaded || serverUrl.isBlank() || apiKey.isBlank()) return
        loaded = true
        currentSortOrder = sortOrder
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val api = ImmichClient.create(serverUrl, apiKey)
                when (source) {
                    "album" -> {
                        val list = api.searchMetadata(
                            SearchMetadataRequest(
                                size = 500,
                                albumIds = listOf(param),
                                order = sortOrder.toApiOrder(),
                            )
                        ).assets.items.filter { it.type.equals("IMAGE", ignoreCase = true) }
                        val ordered = applyClientSort(list, sortOrder)
                        val finalList = if (shuffle) ordered.shuffled() else ordered
                        _state.update { it.copy(loading = false, assets = finalList, index = 0) }
                    }
                    "folder" -> {
                        val list = api.listFolderAssets(param)
                            .filter { it.type.equals("IMAGE", ignoreCase = true) }
                        val ordered = applyClientSort(list, sortOrder)
                        val finalList = if (shuffle) ordered.shuffled() else ordered
                        _state.update { it.copy(loading = false, assets = finalList, index = 0) }
                    }
                    else -> loadRecentPaged(api, shuffle, sortOrder)
                }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Unknown error") }
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

    /**
     * Load "Most recent" across multiple pages so slideshows aren't capped at 500 images.
     * Streams each page into state as it arrives so playback can start on the first page
     * while later pages continue to append in the background.
     */
    private suspend fun loadRecentPaged(api: ImmichApi, shuffle: Boolean, sortOrder: SortOrder) {
        val pageSize = 500
        val maxPages = 20                       // 10 000 image hard cap
        val accumulated = mutableListOf<Asset>()
        var page = 1
        while (page <= maxPages) {
            val resp = api.searchMetadata(
                SearchMetadataRequest(page = page, size = pageSize, order = sortOrder.toApiOrder())
            )
            val items = resp.assets.items
                .filter { it.type.equals("IMAGE", ignoreCase = true) }
            accumulated.addAll(items)
            val snapshot = if (shuffle) accumulated.shuffled() else accumulated.toList()
            _state.update { s ->
                s.copy(
                    loading = false,
                    assets = snapshot,
                    index = s.index.coerceAtMost((snapshot.size - 1).coerceAtLeast(0)),
                )
            }
            val gotFullPage = items.size >= pageSize
            val serverSaysMore = !resp.assets.nextPage.isNullOrBlank()
            if (!gotFullPage && !serverSaysMore) break
            page++
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

    val vmScope get() = viewModelScope

    class Factory(
        private val appContext: Context,
        private val source: String,
        private val param: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SlideshowViewModel(appContext, source, param) as T
    }
}

private fun SortOrder.toApiOrder(): String = when (this) {
    SortOrder.NewestFirst -> "desc"
    SortOrder.OldestFirst -> "asc"
}
