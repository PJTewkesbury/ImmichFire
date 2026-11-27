package com.intec.immichfire.network

/**
 * URL helpers only. Actual HTTP is owned by [com.intec.immichfire.data.RetrofitImmichRepository]
 * via the shared OkHttpClient in [com.intec.immichfire.core.AppContainer].
 */
object ImmichClient {

    fun thumbnailUrl(baseUrl: String, assetId: String, size: String = "thumbnail"): String =
        "${baseUrl.trimEnd('/')}/api/assets/$assetId/thumbnail?size=$size"

    fun originalUrl(baseUrl: String, assetId: String): String =
        "${baseUrl.trimEnd('/')}/api/assets/$assetId/original"
}
