package com.intec.immichfire.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.intec.immichfire.BuildConfig
import com.intec.immichfire.ui.theme.ImmichColors
import com.intec.immichfire.ui.theme.ImmichLogo
import kotlinx.coroutines.delay

/**
 * Compose splash that takes over from the system splash (which can only render an icon +
 * background) so the version / build date / commit hash can also be shown. Held for
 * [holdMillis] then calls [onDone], which the nav graph uses to replace the splash with the
 * real start destination.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SplashScreen(
    onDone: () -> Unit,
    holdMillis: Long = 1_500L,
) {
    LaunchedEffect(Unit) {
        delay(holdMillis)
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(ImmichColors.Background)
            .padding(36.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ImmichLogo(size = 96)
            Text(
                "Immich slideshow for Fire TV",
                color = ImmichColors.OnSurfaceSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        BuildInfoCorner(modifier = Modifier.align(Alignment.BottomEnd))
    }
}

/**
 * Version / build date / commit hash in a right-aligned stack. Pulled from BuildConfig so
 * the APK bakes the identifiers at build time — no runtime git shell-out, which would be
 * both slow and impossible on a Fire TV (git isn't on the device).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BuildInfoCorner(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            "v${BuildConfig.VERSION_NAME}",
            color = ImmichColors.OnSurfaceSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
        Text(
            BuildConfig.BUILD_DATE,
            color = ImmichColors.OnSurfaceMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
        )
        Text(
            BuildConfig.GIT_COMMIT,
            color = ImmichColors.OnSurfaceMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
        )
    }
}
