package com.intec.immichfire.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ImmichClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun create(baseUrl: String, apiKey: String): ImmichApi {
        val normalizedBase = baseUrl.trimEnd('/') + "/"
        // No HTTP logging interceptor: logging every thumbnail request (including the
        // x-api-key header) spams logcat for no production benefit and leaks the key into
        // the device's log buffer where other apps/accessibility tools can scrape it.
        val client = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("x-api-key", apiKey)
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedBase)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ImmichApi::class.java)
    }

    fun thumbnailUrl(baseUrl: String, assetId: String, size: String = "thumbnail"): String =
        "${baseUrl.trimEnd('/')}/api/assets/$assetId/thumbnail?size=$size"

    fun originalUrl(baseUrl: String, assetId: String): String =
        "${baseUrl.trimEnd('/')}/api/assets/$assetId/original"
}
