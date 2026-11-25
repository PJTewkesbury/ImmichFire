package com.intec.immichfire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.intec.immichfire.ui.ImmichFireApp
import com.intec.immichfire.ui.theme.ImmichFireTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImmichFireTheme {
                ImmichFireApp()
            }
        }
    }
}
