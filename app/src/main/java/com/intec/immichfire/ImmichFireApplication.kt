package com.intec.immichfire

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.intec.immichfire.core.AppContainer
import com.intec.immichfire.core.Constants
import okio.Path.Companion.toOkioPath

class ImmichFireApplication : Application(), SingletonImageLoader.Factory {

    /** Lazily built on first access AFTER attachBaseContext — safe for `applicationContext`. */
    val container: AppContainer by lazy { AppContainer(applicationContext) }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // Share the app's single OkHttp with Coil so TLS sessions and connections aren't
        // duplicated between API calls and thumbnail loads.
        val sharedOkHttp = container.okHttp
        return ImageLoader.Builder(context)
            // Memory cache: fraction of the app heap is plenty for a slideshow — we only need
            // the current, previous, and next few decoded previews. The rest comes from disk.
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, Constants.Images.MEMORY_CACHE_HEAP_FRACTION)
                    .build()
            }
            // Disk cache: preview thumbnails are ~100–400 KB each; LRU eviction keeps it from
            // growing. Cap sized for a Fire TV Stick with only ~8 GB total internal storage.
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(Constants.Images.DISK_CACHE_BYTES)
                    .build()
            }
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { sharedOkHttp })) }
            .crossfade(true)
            .build()
    }
}
