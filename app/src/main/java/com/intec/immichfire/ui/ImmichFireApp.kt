package com.intec.immichfire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.intec.immichfire.data.ImmichSettings
import com.intec.immichfire.data.SettingsRepository
import kotlinx.coroutines.flow.first
import com.intec.immichfire.ui.settings.SettingsScreen
import com.intec.immichfire.ui.setup.SetupScreen
import com.intec.immichfire.ui.slideshow.SlideshowScreen
import com.intec.immichfire.ui.sources.SourcePickerScreen
import com.intec.immichfire.ui.theme.ImmichColors
import com.intec.immichfire.ui.theme.ImmichFireTheme

object Routes {
    const val Setup = "setup"
    const val Sources = "sources"
    const val Settings = "settings"
    const val Slideshow = "slideshow/{source}/{param}"
    fun slideshow(source: String, param: String) = "slideshow/$source/${java.net.URLEncoder.encode(param, "UTF-8")}"
}

@Composable
fun ImmichFireApp() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context.applicationContext) }

    var initial by remember { mutableStateOf<ImmichSettings?>(null) }
    LaunchedEffect(Unit) { initial = repo.settings.first() }

    ImmichFireTheme {
        Box(modifier = Modifier.fillMaxSize().background(ImmichColors.Background)) {
            val resolved = initial ?: return@Box
            val nav = rememberNavController()
            val startRoute = if (resolved.isConfigured) Routes.Sources else Routes.Setup
            NavHost(navController = nav, startDestination = startRoute) {
            composable(Routes.Setup) {
                SetupScreen(
                    repo = repo,
                    onDone = {
                        nav.navigate(Routes.Sources) { popUpTo(Routes.Setup) { inclusive = true } }
                    },
                )
            }
            composable(Routes.Sources) {
                SourcePickerScreen(
                    repo = repo,
                    onStartSlideshow = { source, param -> nav.navigate(Routes.slideshow(source, param)) },
                    onOpenSettings = { nav.navigate(Routes.Settings) },
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    repo = repo,
                    onBack = { nav.popBackStack() },
                    onReconfigureServer = {
                        nav.navigate(Routes.Setup) { popUpTo(0) { inclusive = true } }
                    },
                )
            }
            composable(Routes.Slideshow) { entry ->
                val source = entry.arguments?.getString("source") ?: "recent"
                val param = java.net.URLDecoder.decode(entry.arguments?.getString("param") ?: "", "UTF-8")
                SlideshowScreen(
                    repo = repo,
                    source = source,
                    param = param,
                    onExit = { nav.popBackStack() },
                )
            }
            }
        }
    }
}
