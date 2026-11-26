package com.intec.immichfire.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.lightColorScheme

object ImmichColors {
    val Primary = Color(0xFF4250AF)
    val PrimaryVariant = Color(0xFF5B6AD1)
    val PrimaryBorder = Color(0xFFACB1DC)
    val PrimarySubtle = Color(0xFFE4E5F5)
    val PrimaryFocus = Color(0xFF2E3A8C)

    val Background = Color(0xFFF6F6F4)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFFBFBFC)

    val OnBackground = Color(0xFF1F1F1F)
    val OnSurface = Color(0xFF1F1F1F)
    val OnSurfaceSecondary = Color(0xFF6B6B6B)
    val OnSurfaceMuted = Color(0xFF9A9A9A)

    val Error = Color(0xFFB00020)
    val Divider = Color(0xFFE6E6EA)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ImmichFireTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = ImmichColors.Primary,
            onPrimary = Color.White,
            background = ImmichColors.Background,
            onBackground = ImmichColors.OnBackground,
            surface = ImmichColors.Surface,
            onSurface = ImmichColors.OnSurface,
        ),
        content = content,
    )
}
