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

    /**
     * Sign in with the user's Immich email + password, mint a new API key, verify it with
     * a ping, and persist it. Password is never stored — only the generated key is.
     */
    suspend fun loginAndSave(
        url: String,
        email: String,
        password: String,
        keyName: String,
    ): ApiResult<Unit> {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || email.isBlank() || password.isBlank()) {
            return ApiResult.Failure(ImmichError.NotConfigured)
        }
        val minted = repo.loginAndCreateApiKey(cleanUrl, email, password, keyName)
        val key = (minted as? ApiResult.Success)?.value
            ?: return minted as ApiResult.Failure
        return verifyAndSave(cleanUrl, key)
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
