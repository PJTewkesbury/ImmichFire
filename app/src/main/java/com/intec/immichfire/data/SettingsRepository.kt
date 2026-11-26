package com.intec.immichfire.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "immichfire_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val API_KEY = stringPreferencesKey("api_key")
        val INTERVAL_MS = longPreferencesKey("slideshow_interval_ms")
        val TRANSITION = stringPreferencesKey("transition")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val LOOP = booleanPreferencesKey("loop")
        val FIT = stringPreferencesKey("fit")
        val SORT_ORDER = stringPreferencesKey("sort_order")
    }

    val settings: Flow<ImmichSettings> = context.dataStore.data.map { p ->
        ImmichSettings(
            serverUrl = p[Keys.SERVER_URL].orEmpty(),
            apiKey = p[Keys.API_KEY].orEmpty(),
            slideshowIntervalMs = p[Keys.INTERVAL_MS] ?: 5_000L,
            transition = runCatching { Transition.valueOf(p[Keys.TRANSITION] ?: Transition.Crossfade.name) }.getOrDefault(Transition.Crossfade),
            shuffle = p[Keys.SHUFFLE] ?: false,
            loop = p[Keys.LOOP] ?: true,
            fitMode = runCatching { FitMode.valueOf(p[Keys.FIT] ?: FitMode.Fit.name) }.getOrDefault(FitMode.Fit),
            sortOrder = runCatching { SortOrder.valueOf(p[Keys.SORT_ORDER] ?: SortOrder.NewestFirst.name) }.getOrDefault(SortOrder.NewestFirst),
        )
    }

    suspend fun saveServer(url: String, apiKey: String) {
        context.dataStore.edit {
            it[Keys.SERVER_URL] = url.trimEnd('/')
            it[Keys.API_KEY] = apiKey.trim()
        }
    }

    suspend fun update(block: (ImmichSettings) -> ImmichSettings) {
        context.dataStore.edit { p ->
            val current = ImmichSettings(
                serverUrl = p[Keys.SERVER_URL].orEmpty(),
                apiKey = p[Keys.API_KEY].orEmpty(),
                slideshowIntervalMs = p[Keys.INTERVAL_MS] ?: 5_000L,
                transition = runCatching { Transition.valueOf(p[Keys.TRANSITION] ?: Transition.Crossfade.name) }.getOrDefault(Transition.Crossfade),
                shuffle = p[Keys.SHUFFLE] ?: false,
                loop = p[Keys.LOOP] ?: true,
                fitMode = runCatching { FitMode.valueOf(p[Keys.FIT] ?: FitMode.Fit.name) }.getOrDefault(FitMode.Fit),
                sortOrder = runCatching { SortOrder.valueOf(p[Keys.SORT_ORDER] ?: SortOrder.NewestFirst.name) }.getOrDefault(SortOrder.NewestFirst),
            )
            val next = block(current)
            p[Keys.SERVER_URL] = next.serverUrl
            p[Keys.API_KEY] = next.apiKey
            p[Keys.INTERVAL_MS] = next.slideshowIntervalMs
            p[Keys.TRANSITION] = next.transition.name
            p[Keys.SHUFFLE] = next.shuffle
            p[Keys.LOOP] = next.loop
            p[Keys.FIT] = next.fitMode.name
            p[Keys.SORT_ORDER] = next.sortOrder.name
        }
    }
}
