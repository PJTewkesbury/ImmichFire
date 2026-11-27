package com.intec.immichfire.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.intec.immichfire.data.ImmichSettings
import com.intec.immichfire.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {

    val settings: StateFlow<ImmichSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ImmichSettings())

    fun update(block: (ImmichSettings) -> ImmichSettings) {
        viewModelScope.launch { repo.update(block) }
    }

    fun clearServer() {
        viewModelScope.launch { repo.saveServer("", "") }
    }

    class Factory(private val repo: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(repo) as T
    }
}
