package com.intec.immichfire.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.intec.immichfire.data.SortOrder
import com.intec.immichfire.data.Transition
import com.intec.immichfire.ui.theme.ImmichChip
import com.intec.immichfire.ui.theme.ImmichColors
import com.intec.immichfire.ui.theme.ImmichSectionCard
import com.intec.immichfire.ui.theme.ImmichToggle
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
            .background(ImmichColors.Background)
            .padding(horizontal = 56.dp, vertical = 40.dp)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Settings",
                        color = ImmichColors.OnBackground,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Manage the app features",
                        color = ImmichColors.OnSurfaceSecondary,
                        fontSize = 14.sp,
                    )
                }
                SecondaryButton("Back", onClick = onBack)
            }

            ImmichSectionCard(
                title = "Server",
                subtitle = "The Immich instance this device is paired with.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabelValue("URL", settings.serverUrl.ifBlank { "(not set)" })
                    LabelValue(
                        "API key",
                        if (settings.apiKey.isBlank()) "(not set)" else "•".repeat(12),
                    )
                }
            }

            ImmichSectionCard(
                title = "Slideshow speed",
                subtitle = "How long each photo stays on screen.",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3_000L, 5_000L, 10_000L, 30_000L, 60_000L, 300_000L).forEach { ms ->
                        val label = if (ms >= 60_000) "${ms / 60_000}m" else "${ms / 1000}s"
                        ImmichChip(label, settings.slideshowIntervalMs == ms) {
                            scope.launch { repo.update { it.copy(slideshowIntervalMs = ms) } }
                        }
                    }
                }
            }

            ImmichSectionCard(
                title = "Transition",
                subtitle = "Effect applied when moving between photos.",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Transition.entries.forEach { t ->
                        ImmichChip(t.name, settings.transition == t) {
                            scope.launch { repo.update { it.copy(transition = t) } }
                        }
                    }
                }
            }

            ImmichSectionCard(
                title = "Fit",
                subtitle = "How each photo is scaled to the screen.",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FitMode.entries.forEach { f ->
                        ImmichChip(f.name, settings.fitMode == f) {
                            scope.launch { repo.update { it.copy(fitMode = f) } }
                        }
                    }
                }
            }

            ImmichSectionCard(
                title = "Photo order",
                subtitle = "Which photo plays first — by date the photo was taken.",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImmichChip("Newest first", settings.sortOrder == SortOrder.NewestFirst) {
                        scope.launch { repo.update { it.copy(sortOrder = SortOrder.NewestFirst) } }
                    }
                    ImmichChip("Oldest first", settings.sortOrder == SortOrder.OldestFirst) {
                        scope.launch { repo.update { it.copy(sortOrder = SortOrder.OldestFirst) } }
                    }
                }
            }

            ImmichSectionCard(
                title = "Playback",
                subtitle = "Randomise order and loop at the end of a source.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ToggleRow(
                        label = "Shuffle",
                        description = "Play photos in a random order",
                        on = settings.shuffle,
                        onToggle = { scope.launch { repo.update { it.copy(shuffle = !it.shuffle) } } },
                    )
                    ToggleRow(
                        label = "Loop",
                        description = "Restart from the beginning when the end is reached",
                        on = settings.loop,
                        onToggle = { scope.launch { repo.update { it.copy(loop = !it.loop) } } },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Reconfigure server (URL + API key)") {
                    scope.launch {
                        repo.saveServer("", "")
                        onReconfigureServer()
                    }
                }
            }
            Spacer(Modifier.width(1.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LabelValue(label: String, value: String) {
    Column {
        Text(label, color = ImmichColors.OnSurfaceSecondary, fontSize = 13.sp)
        Text(value, color = ImmichColors.OnSurface, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ToggleRow(
    label: String,
    description: String,
    on: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = ImmichColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(description, color = ImmichColors.OnSurfaceSecondary, fontSize = 13.sp)
        }
        ImmichToggle(on, onToggle)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (focused) ImmichColors.Primary else ImmichColors.Surface)
            .border(
                1.dp,
                if (focused) ImmichColors.PrimaryFocus else ImmichColors.PrimaryBorder,
                RoundedCornerShape(22.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick(); true
                } else false
            }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (focused) Color.White else ImmichColors.Primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
