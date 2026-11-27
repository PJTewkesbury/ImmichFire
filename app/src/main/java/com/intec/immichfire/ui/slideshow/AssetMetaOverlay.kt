package com.intec.immichfire.ui.slideshow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.intec.immichfire.network.Asset

/** Date + locality + rating card shown in the bottom-right corner of the slideshow. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AssetMetaOverlay(asset: Asset, modifier: Modifier = Modifier) {
    val dateText = formatAssetDate(asset)
    val locality = listOfNotNull(
        asset.exifInfo?.city,
        asset.exifInfo?.state,
        asset.exifInfo?.country,
    ).joinToString(", ").takeIf { it.isNotBlank() }
    val rating = asset.exifInfo?.rating?.takeIf { it in 1..5 }

    if (dateText == null && locality == null && rating == null) return

    Column(
        modifier.background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        dateText?.let { Text(it, color = Color.White, fontSize = 14.sp) }
        locality?.let { Text(it, color = Color(0xFFCCCCCC), fontSize = 13.sp) }
        rating?.let {
            Text("★".repeat(it) + "☆".repeat(5 - it), color = Color(0xFFFFC107), fontSize = 14.sp)
        }
    }
}
