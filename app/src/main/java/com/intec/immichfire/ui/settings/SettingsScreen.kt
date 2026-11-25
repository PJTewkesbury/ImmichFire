package com.intec.immichfire.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.intec.immichfire.data.FitMode
import com.intec.immichfire.data.ImmichSettings
import com.intec.immichfire.data.SettingsRepository
import com.intec.immichfire.data.Transition
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    repo: SettingsRepository,
    onBack: () -> Unit,
    onReconfigureServer: () -> Unit = {},
) {
    val settings by repo.settings.collectAsState(initial = ImmichSettings())
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0B))
            .padding(48.dp)
            .onKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.Back || it.key == Key.Escape)) {
                    onBack(); true
                } else false
            },
    ) {
        Column(
            modifier = Modifier.verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "Settings",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.focusable(),
            )
            Column(
                modifier = Modifier.focusable(),
            ) {
                Text("Server", color = Color(0xFFAAAAAA), fontSize = 14.sp)
                Text(settings.serverUrl.ifBlank { "(not set)" }, color = Color.White, fontSize = 18.sp)
            }
            Column(
                modifier = Modifier.focusable(),
            ) {
                Text("API key", color = Color(0xFFAAAAAA), fontSize = 14.sp)
                Text(if (settings.apiKey.isBlank()) "(not set)" else "•".repeat(12), color = Color.White, fontSize = 18.sp)
            }

            SettingRow("Slideshow speed") {
                listOf(3_000L, 5_000L, 10_000L, 30_000L, 60_000L, 300_000L).forEach { ms ->
                    val label = if (ms >= 60_000) "${ms / 60_000}m" else "${ms / 1000}s"
                    Chip(label, settings.slideshowIntervalMs == ms) {
                        scope.launch { repo.update { it.copy(slideshowIntervalMs = ms) } }
                    }
                }
            }
            SettingRow("Transition") {
                Transition.entries.forEach { t ->
                    Chip(t.name, settings.transition == t) {
                        scope.launch { repo.update { it.copy(transition = t) } }
                    }
                }
            }
            SettingRow("Fit") {
                FitMode.entries.forEach { f ->
                    Chip(f.name, settings.fitMode == f) {
                        scope.launch { repo.update { it.copy(fitMode = f) } }
                    }
                }
            }
            SettingRow("Shuffle") {
                Chip(if (settings.shuffle) "On" else "Off", true) {
                    scope.launch { repo.update { it.copy(shuffle = !it.shuffle) } }
                }
            }
            SettingRow("Loop") {
                Chip(if (settings.loop) "On" else "Off", true) {
                    scope.launch { repo.update { it.copy(loop = !it.loop) } }
                }
            }
            Spacer(Modifier.width(24.dp))
            Chip("Reconfigure server (URL + API key)", false) {
                scope.launch {
                    repo.saveServer("", "")
                    onReconfigureServer()
                }
            }
            Chip("Back", false, onClick = onBack)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, color = Color(0xFFAAAAAA), fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .background(
                when {
                    focused -> Color(0xFFFF6600)
                    selected -> Color(0xFF444444)
                    else -> Color(0xFF1E1E1E)
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick(); true
                } else false
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 14.sp) }
}
