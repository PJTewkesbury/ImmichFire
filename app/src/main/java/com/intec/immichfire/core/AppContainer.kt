package com.intec.immichfire.core

import android.content.Context
import com.intec.immichfire.ImmichFireApplication
import com.intec.immichfire.data.Credentials
import com.intec.immichfire.data.ImmichRepository
import com.intec.immichfire.data.ImmichSettings
import com.intec.immichfire.data.RetrofitImmichRepository
import com.intec.immichfire.data.SettingsRepository
import com.intec.immichfire.data.toCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/**
 * Simple Service Locator for the app. Owns the shared [OkHttpClient] (used by both Retrofit
 * and Coil so TLS sessions and connection pools are reused), the single [SettingsRepository],
 * and the [ImmichRepository] the UI layer talks to. Scoped to the Application lifetime —
 * never leaked into Activities.
 *
 * Deliberately NOT a DI framework: one module, a handful of dependencies, no need for Hilt.
 */
class AppContainer(appContext: Context) {

    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** One OkHttp instance for the whole app. Coil's network fetcher and Retrofit both wrap
     *  this via .newBuilder(), so TLS sessions, DNS cache, and the connection pool are shared.
     */
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(Constants.Network.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(Constants.Network.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    val settingsRepository: SettingsRepository = SettingsRepository(appContext)

    /** Hot settings flow, so start destination and the various ViewModels can observe without
     *  re-reading DataStore per subscriber. */
    val settings: StateFlow<ImmichSettings?> = settingsRepository.settings
        .stateIn(
            scope = containerScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = null,
        )

    /** Current credentials, kept in a StateFlow the repository reads on every call. */
    private val _credentials = MutableStateFlow(Credentials("", ""))
    val credentials: StateFlow<Credentials> = _credentials.asStateFlow()

    init {
        // Mirror settings → credentials. The repository reads `credentials.value` on every
        // call, so saving a new server URL takes effect immediately for the next request.
        containerScope.launch {
            settingsRepository.settings
                .map { it.toCredentials() }
                .collect { _credentials.value = it }
        }
    }

    val immichRepository: ImmichRepository = RetrofitImmichRepository(okHttp, credentials)
}

/** Container accessor for any context. Safe to call from Activities, Composables, VMs. */
val Context.appContainer: AppContainer
    get() = (applicationContext as ImmichFireApplication).container

/** Settings flow helper, for places (e.g. ViewModel factories) where [appContainer] is awkward. */
val Context.settingsFlow: Flow<ImmichSettings?>
    get() = appContainer.settings
