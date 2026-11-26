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
            // Memory cache: 15% of the app heap is plenty for a slideshow — we only need the
            // current, previous, and next few decoded previews. The rest comes from disk.
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
            // Disk cache: cap at 64 MB. Preview thumbnails are ~100–400 KB each, so this still
            // holds hundreds of images; LRU eviction keeps it from growing. 512 MB was
            // overkill for a Fire TV Stick with only ~8 GB total internal storage.
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttp })) }
            .crossfade(true)
            .build()
    }
}
