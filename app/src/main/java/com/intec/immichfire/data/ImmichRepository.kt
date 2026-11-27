package com.intec.immichfire.data

import com.intec.immichfire.network.AlbumSummary
import com.intec.immichfire.network.Asset

/**
 * Domain-level seam between the ViewModels and the Immich API. ViewModels depend on this
 * interface, not Retrofit — so tests can swap in a fake, and switching HTTP clients wouldn't
 * ripple into the UI layer.
 *
 * Every call returns an [ApiResult] so callers handle the typed error ([ImmichError])
 * instead of catching HttpException / IOException themselves.
 */
interface ImmichRepository {

    /** Lightweight connectivity check against `/api/server/ping`. */
    suspend fun ping(credentials: Credentials): ApiResult<Unit>

    /**
     * Sign in with Immich email + password, then mint a new API key named [keyName] with
     * "all" permissions (matching what the Immich UI gives a user-created key by default).
     * Returns the raw key secret on success — callers typically ping with it and persist it
     * as [Credentials.apiKey]. Never saves the password; the password only lives in memory
     * for the two HTTPS calls.
     */
    suspend fun loginAndCreateApiKey(
        baseUrl: String,
        email: String,
        password: String,
        keyName: String,
    ): ApiResult<String>

    /** Albums with a non-blank name and at least one asset, newest-first. */
    suspend fun listAlbums(): ApiResult<List<AlbumSummary>>

    /** Immich folder-view unique paths. */
    suspend fun listFolderPaths(): ApiResult<List<String>>

    /** All external-library import paths across configured libraries, for prefix stripping. */
    suspend fun listLibraryImportPaths(): ApiResult<List<String>>

    /** Image assets in the given folder path. */
    suspend fun listFolderAssets(path: String): ApiResult<List<Asset>>

    /** Image assets in an album (paged + sorted per [sortOrder]). */
    suspend fun listAlbumAssets(albumId: String, sortOrder: SortOrder): ApiResult<List<Asset>>

    /**
     * Stream "most recent" photos page-by-page. Each page's accumulated list is emitted via
     * [onPage] so the slideshow can start on the first page while later ones append. Returns
     * the final accumulated list on success.
     */
    suspend fun listRecentAssetsPaged(
        sortOrder: SortOrder,
        onPage: (List<Asset>) -> Unit,
    ): ApiResult<List<Asset>>
}

/** Credential pair. The repository uses whichever credentials are current when it's called. */
data class Credentials(val baseUrl: String, val apiKey: String) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()
}

fun ImmichSettings.toCredentials(): Credentials = Credentials(serverUrl, apiKey)
