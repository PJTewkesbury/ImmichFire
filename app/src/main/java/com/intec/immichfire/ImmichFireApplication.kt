package com.intec.immichfire

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

class ImmichFireApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val okHttp = OkHttpClient.Builder().build()
        return ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("image_cache").toOkioPath()).maxSizeBytes(512L * 1024 * 1024).build() }
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttp })) }
            .crossfade(true)
            .build()
    }
}
