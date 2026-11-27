package com.intec.immichfire.core

/**
 * App-wide constants. Lives here so magic numbers and shared config don't drift between
 * screens (e.g. the slideshow interval list used by both the Settings screen and the
 * on-screen overlay in the Slideshow).
 */
object Constants {

    object Network {
        /** OkHttp connect timeout for Immich API calls. */
        const val CONNECT_TIMEOUT_SECONDS: Long = 15
        /** OkHttp read timeout for Immich API calls. */
        const val READ_TIMEOUT_SECONDS: Long = 30
        /** Setup HTTP server listens here; must match the URL in the phone-pairing QR. */
        const val SETUP_SERVER_PORT: Int = 8080
    }

    object Discovery {
        /** Default Immich listen port probed during LAN discovery. */
        const val DEFAULT_IMMICH_PORT: Int = 2283
        const val SUBNET_PROBE_CONNECT_TIMEOUT_MS: Int = 350
        const val SUBNET_PROBE_READ_TIMEOUT_MS: Int = 700
        const val SUBNET_PROBE_TOTAL_BUDGET_MS: Long = 4_000
        const val MDNS_TOTAL_BUDGET_MS: Long = 5_000
        const val MDNS_IMMICH_LIST_TIMEOUT_MS: Long = 3_500
        const val MDNS_HTTP_LIST_TIMEOUT_MS: Long = 2_000
        const val SUBNET_THREAD_POOL_SIZE: Int = 64
    }

    object Images {
        /** Coil disk cache size. 64 MB fits hundreds of previews; Fire TV Sticks only have ~8 GB. */
        const val DISK_CACHE_BYTES: Long = 64L * 1024 * 1024
        /** Fraction of the app heap used for Coil's memory cache. */
        const val MEMORY_CACHE_HEAP_FRACTION: Double = 0.15
    }

    object Slideshow {
        /** The intervals offered in both the Settings chips and the slideshow overlay. */
        val INTERVAL_MS_CHOICES: List<Long> = listOf(3_000L, 5_000L, 10_000L, 30_000L, 60_000L, 300_000L)
        /** Overlay auto-dismiss after this much idle time. */
        const val OVERLAY_AUTO_HIDE_MS: Long = 6_000
        /** How many upcoming slides to prefetch into Coil. */
        const val PREFETCH_AHEAD: Int = 3

        // Zoom
        const val ZOOM_LONG_PRESS_MS: Long = 500
        const val ZOOM_INITIAL: Float = 1.5f
        const val ZOOM_STEP: Float = 0.5f
        const val ZOOM_MAX: Float = 4f
        const val ZOOM_PAN_STEP_PX: Float = 80f

        // Paged "Most recent" fetch
        const val PAGE_SIZE: Int = 500
        const val MAX_PAGES: Int = 20

        // Ken Burns animation bounds
        const val KEN_BURNS_SCALE_FROM: Float = 1.08f
        const val KEN_BURNS_SCALE_TO: Float = 1.25f
        const val KEN_BURNS_TRANSLATE_FRAC: Float = 0.08f
        const val KEN_BURNS_TRANSLATE_FRAC_Y: Float = 0.06f
        const val KEN_BURNS_MIN_DURATION_MS: Int = 4_000
    }
}
