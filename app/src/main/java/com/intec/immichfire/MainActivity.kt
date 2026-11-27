package com.intec.immichfire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.intec.immichfire.core.appContainer
import com.intec.immichfire.ui.ImmichFireApp
import com.intec.immichfire.ui.theme.ImmichFireTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be installed BEFORE super.onCreate(). Holds the system splash on-screen until
        // the settings StateFlow has emitted its first value, so the user never sees the
        // short "empty cream box" frame that used to render while DataStore loaded.
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { appContainer.settings.value == null }

        super.onCreate(savedInstanceState)
        setContent {
            ImmichFireTheme {
                ImmichFireApp()
            }
        }
    }
}
