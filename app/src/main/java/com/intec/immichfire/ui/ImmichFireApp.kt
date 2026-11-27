package com.intec.immichfire.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.intec.immichfire.core.appContainer
import com.intec.immichfire.ui.settings.SettingsScreen
import com.intec.immichfire.ui.setup.SetupScreen
import com.intec.immichfire.ui.slideshow.SlideshowScreen
import com.intec.immichfire.ui.sources.SourcePickerScreen
import com.intec.immichfire.ui.theme.ImmichColors

object Routes {
    const val Setup = "setup"
    const val Sources = "sources"
    const val Settings = "settings"
    const val Slideshow = "slideshow/{source}/{param}"

    /** Build a slideshow route with `param` safely URL-encoded for a path segment. */
    fun slideshow(source: String, param: String) = "slideshow/$source/${Uri.encode(param)}"
}

@Composable
fun ImmichFireApp() {
    val context = LocalContext.current
    val container = context.appContainer

    // Observe settings reactively so the start destination updates after Reconfigure (null
    // = still loading the first value; show an empty background until it arrives).
    val settings by container.settings.collectAsState()
    val resolved = settings ?: run {
        Box(modifier = Modifier.fillMaxSize().background(ImmichColors.Background))
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(ImmichColors.Background)) {
        val nav = rememberNavController()
        val startRoute = if (resolved.isConfigured) Routes.Sources else Routes.Setup
        NavHost(navController = nav, startDestination = startRoute) {
            composable(Routes.Setup) {
                SetupScreen(
                    onDone = {
                        nav.navigate(Routes.Sources) { popUpTo(Routes.Setup) { inclusive = true } }
                    },
                )
            }
            composable(Routes.Sources) {
                SourcePickerScreen(
                    onStartSlideshow = { source, param -> nav.navigate(Routes.slideshow(source, param)) },
                    onOpenSettings = { nav.navigate(Routes.Settings) },
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onReconfigureServer = {
                        nav.navigate(Routes.Setup) { popUpTo(0) { inclusive = true } }
                    },
                )
            }
            composable(Routes.Slideshow) { entry ->
                val source = entry.arguments?.getString("source") ?: "recent"
                val param = Uri.decode(entry.arguments?.getString("param") ?: "")
                SlideshowScreen(
                    repo = container.settingsRepository,
                    source = source,
                    param = param,
                    onExit = { nav.popBackStack() },
                )
            }
        }
    }
}
