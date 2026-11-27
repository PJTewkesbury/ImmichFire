package com.intec.immichfire.data

import com.intec.immichfire.core.Constants
import com.intec.immichfire.network.AlbumSummary
import com.intec.immichfire.network.Asset
import com.intec.immichfire.network.CreateApiKeyRequest
import com.intec.immichfire.network.ImmichApi
import com.intec.immichfire.network.LoginRequest
import com.intec.immichfire.network.SearchMetadataRequest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Concrete [ImmichRepository] backed by Retrofit on a shared [OkHttpClient]. Retrofit/Api
 * instances are cached per credential tuple, so switching servers doesn't leak clients and
 * repeat calls on the same tuple reuse the same connection pool (fixes the "fresh client per
 * call" perf finding).
 *
 * The caller provides a [credentials] StateFlow — current values are read on every call.
 */
class RetrofitImmichRepository(
    private val baseOkHttp: OkHttpClient,
    private val credentials: StateFlow<Credentials>,
) : ImmichRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Volatile private var cache: Pair<Credentials, ImmichApi>? = null

    /**
     * @return the cached [ImmichApi] for the current credentials, or null when the user
     *   hasn't set up a server yet.
     */
    private fun apiForCurrent(): ImmichApi? {
        val cur = credentials.value
        if (!cur.isConfigured) return null
        val existing = cache
        if (existing != null && existing.first == cur) return existing.second
        val authed = baseOkHttp.newBuilder()
            // Key only goes on Immich-host requests: don't followRedirects to any other host
            // or the x-api-key header would ride along (OkHttp doesn't strip custom headers
            // on cross-host redirects).
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("x-api-key", cur.apiKey)
                        .addHeader("Accept", "application/json")
                        .build()
                )
            }
            .build()
        val api = Retrofit.Builder()
            .baseUrl(cur.baseUrl.trimEnd('/') + "/")
            .client(authed)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ImmichApi::class.java)
        cache = cur to api
        return api
    }

    private suspend inline fun <T> call(crossinline op: suspend (ImmichApi) -> T): ApiResult<T> {
        val a = apiForCurrent() ?: return ApiResult.Failure(ImmichError.NotConfigured)
        return runCatching { op(a) }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = { ApiResult.Failure(ImmichError.from(it)) },
        )
    }

    /**
     * Build a Retrofit [ImmichApi] against [baseUrl] that does NOT attach the x-api-key
     * header — used by login + create-api-key, which can't present a key yet (login) or use
     * a bearer token instead (create-api-key). Also mirrors [ping]'s no-follow-redirect
     * behavior so no header ever rides along to another host.
     */
    private fun noAuthApi(baseUrl: String): ImmichApi {
        val client = baseOkHttp.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("Accept", "application/json")
                        .build()
                )
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ImmichApi::class.java)
    }

    override suspend fun loginAndCreateApiKey(
        baseUrl: String,
        email: String,
        password: String,
        keyName: String,
    ): ApiResult<String> {
        if (baseUrl.isBlank() || email.isBlank() || password.isBlank()) {
            return ApiResult.Failure(ImmichError.NotConfigured)
        }
        val api = noAuthApi(baseUrl)
        return runCatching {
            val login = api.login(LoginRequest(email = email.trim(), password = password))
            val key = api.createApiKey(
                bearer = "Bearer ${login.accessToken}",
                req = CreateApiKeyRequest(name = keyName),
            )
            key.secret
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = { ApiResult.Failure(ImmichError.from(it)) },
        )
    }

    override suspend fun ping(credentials: Credentials): ApiResult<Unit> {
        // Explicit creds so SetupViewModel can test unsaved credentials without having to
        // save them first.
        if (!credentials.isConfigured) return ApiResult.Failure(ImmichError.NotConfigured)
        val oneOff = baseOkHttp.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("x-api-key", credentials.apiKey)
                        .addHeader("Accept", "application/json")
                        .build()
                )
            }
            .build()
        val api = Retrofit.Builder()
            .baseUrl(credentials.baseUrl.trimEnd('/') + "/")
            .client(oneOff)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ImmichApi::class.java)
        return runCatching { api.ping() }.fold(
            onSuccess = { ApiResult.Success(Unit) },
            onFailure = { ApiResult.Failure(ImmichError.from(it)) },
        )
    }

    override suspend fun listAlbums(): ApiResult<List<AlbumSummary>> = call { api ->
        api.listAlbums()
            .filter { it.assetCount > 0 && it.name.isNotBlank() }
            .sortedByDescending { it.createdAt ?: "" }
    }

    override suspend fun listFolderPaths(): ApiResult<List<String>> = call { it.listFolderPaths() }

    override suspend fun listLibraryImportPaths(): ApiResult<List<String>> = call { api ->
        api.listLibraries().flatMap { it.importPaths }
    }

    override suspend fun listFolderAssets(path: String): ApiResult<List<Asset>> = call { api ->
        api.listFolderAssets(path).filter { it.type.equals("IMAGE", ignoreCase = true) }
    }

    override suspend fun listAlbumAssets(
        albumId: String,
        sortOrder: SortOrder,
    ): ApiResult<List<Asset>> = call { api ->
        api.searchMetadata(
            SearchMetadataRequest(
                size = Constants.Slideshow.PAGE_SIZE,
                albumIds = listOf(albumId),
                order = sortOrder.toApiOrder(),
            )
        ).assets.items.filter { it.type.equals("IMAGE", ignoreCase = true) }
    }

    override suspend fun listRecentAssetsPaged(
        sortOrder: SortOrder,
        onPage: (List<Asset>) -> Unit,
    ): ApiResult<List<Asset>> {
        val api = apiForCurrent() ?: return ApiResult.Failure(ImmichError.NotConfigured)
        val accumulated = mutableListOf<Asset>()
        var page = 1
        return runCatching {
            while (page <= Constants.Slideshow.MAX_PAGES) {
                val resp = api.searchMetadata(
                    SearchMetadataRequest(
                        page = page,
                        size = Constants.Slideshow.PAGE_SIZE,
                        order = sortOrder.toApiOrder(),
                    )
                )
                val items = resp.assets.items.filter { it.type.equals("IMAGE", ignoreCase = true) }
                accumulated.addAll(items)
                onPage(accumulated.toList())
                val gotFullPage = items.size >= Constants.Slideshow.PAGE_SIZE
                val serverSaysMore = !resp.assets.nextPage.isNullOrBlank()
                if (!gotFullPage && !serverSaysMore) break
                page++
            }
            accumulated.toList()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = { ApiResult.Failure(ImmichError.from(it)) },
        )
    }
}

private fun SortOrder.toApiOrder(): String = when (this) {
    SortOrder.NewestFirst -> "desc"
    SortOrder.OldestFirst -> "asc"
}
