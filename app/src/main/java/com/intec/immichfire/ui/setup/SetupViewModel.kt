package com.intec.immichfire.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.intec.immichfire.data.ApiResult
import com.intec.immichfire.data.Credentials
import com.intec.immichfire.data.ImmichError
import com.intec.immichfire.data.ImmichRepository
import com.intec.immichfire.data.SettingsRepository

/**
 * Setup is driven mostly by the embedded HTTP server; the only real "ViewModel work" is
 * ping + save when the phone submits credentials. Keeping it in a VM so the screen
 * composable has no network/data imports.
 */
class SetupViewModel(
    private val repo: ImmichRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    suspend fun verifyAndSave(url: String, apiKey: String): ApiResult<Unit> {
        val cred = Credentials(url.trim(), apiKey.trim())
        if (!cred.isConfigured) return ApiResult.Failure(ImmichError.NotConfigured)
        val pong = repo.ping(cred)
        if (pong is ApiResult.Failure) return pong
        settings.saveServer(cred.baseUrl, cred.apiKey)
        return ApiResult.Success(Unit)
    }

    suspend fun testOnly(url: String, apiKey: String): ApiResult<Unit> =
        repo.ping(Credentials(url.trim(), apiKey.trim()))

    class Factory(
        private val repo: ImmichRepository,
        private val settings: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SetupViewModel(repo, settings) as T
    }
}
