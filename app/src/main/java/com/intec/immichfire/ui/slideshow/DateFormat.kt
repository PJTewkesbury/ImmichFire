package com.intec.immichfire.ui.slideshow

import com.intec.immichfire.network.Asset
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private val DISPLAY_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy")

/** Best-effort "when was this photo taken" display string, falling back through EXIF → local
 *  → file-created → the first 10 chars of the raw timestamp. */
internal fun formatAssetDate(asset: Asset): String? {
    val raw = asset.exifInfo?.dateTimeOriginal
        ?: asset.localDateTime
        ?: asset.fileCreatedAt
        ?: return null
    return runCatching { OffsetDateTime.parse(raw).format(DISPLAY_FORMAT) }
        .getOrElse {
            runCatching {
                LocalDateTime.parse(raw.removeSuffix("Z")).format(DISPLAY_FORMAT)
            }.getOrNull() ?: raw.take(10)
        }
}
