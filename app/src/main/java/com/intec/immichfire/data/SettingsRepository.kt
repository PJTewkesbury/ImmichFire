package com.intec.immichfire.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
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

    val settings: Flow<ImmichSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun saveServer(url: String, apiKey: String) {
        context.dataStore.edit {
            // Prepend a scheme if the user typed a bare host:port (e.g. "192.168.0.100:2283"),
            // otherwise Retrofit throws when constructing the client.
            val normalized = url.trim().let { raw ->
                if (raw.isBlank() || raw.contains("://")) raw else "http://$raw"
            }
            it[Keys.SERVER_URL] = normalized.trimEnd('/')
            it[Keys.API_KEY] = apiKey.trim()
        }
    }

    suspend fun update(block: (ImmichSettings) -> ImmichSettings) {
        context.dataStore.edit { p ->
            val next = block(p.toSettings())
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

    private fun Preferences.toSettings() = ImmichSettings(
        serverUrl = this[Keys.SERVER_URL].orEmpty(),
        apiKey = this[Keys.API_KEY].orEmpty(),
        slideshowIntervalMs = this[Keys.INTERVAL_MS] ?: 5_000L,
        transition = parseEnum(this[Keys.TRANSITION], Transition.Crossfade),
        shuffle = this[Keys.SHUFFLE] ?: false,
        loop = this[Keys.LOOP] ?: true,
        fitMode = parseEnum(this[Keys.FIT], FitMode.Fit),
        sortOrder = parseEnum(this[Keys.SORT_ORDER], SortOrder.NewestFirst),
    )

    private inline fun <reified E : Enum<E>> parseEnum(raw: String?, default: E): E =
        runCatching { enumValueOf<E>(raw ?: default.name) }.getOrDefault(default)
}
