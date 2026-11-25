package com.intec.immichfire.ui.slideshow

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.intec.immichfire.network.Asset
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

    fun load(serverUrl: String, apiKey: String, shuffle: Boolean) {
        if (loaded || serverUrl.isBlank() || apiKey.isBlank()) return
        loaded = true
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val api = ImmichClient.create(serverUrl, apiKey)
                val list = when (source) {
                    "album" -> api.searchMetadata(
                        SearchMetadataRequest(size = 500, albumIds = listOf(param))
                    ).assets.items
                    "folder" -> api.listFolderAssets(param)
                    else -> api.searchMetadata(SearchMetadataRequest(size = 500)).assets.items
                }.filter { it.type.equals("IMAGE", ignoreCase = true) }
                val finalList = if (shuffle) list.shuffled() else list
                _state.update { it.copy(loading = false, assets = finalList, index = 0) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Unknown error") }
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
