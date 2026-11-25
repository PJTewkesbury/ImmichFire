package com.intec.immichfire.ui.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.intec.immichfire.data.SettingsRepository
import com.intec.immichfire.network.AlbumSummary
import com.intec.immichfire.network.ImmichClient

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SourcePickerScreen(
    repo: SettingsRepository,
    onStartSlideshow: (source: String, param: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val settings by repo.settings.collectAsState(initial = com.intec.immichfire.data.ImmichSettings())
    val scope = rememberCoroutineScope()

    var albums by remember { mutableStateOf<List<AlbumSummary>>(emptyList()) }
    var folders by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settings.serverUrl, settings.apiKey) {
        if (settings.isConfigured) {
            runCatching {
                val api = ImmichClient.create(settings.serverUrl, settings.apiKey)
                albums = api.listAlbums()
                folders = runCatching { api.listFolderPaths() }.getOrDefault(emptyList())
            }.onFailure { error = it.message }
        }
    }

    val focusRecent = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRecent.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0B)).padding(48.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ImmichFire", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(24.dp))
                Text(settings.serverUrl, color = Color(0xFF888888), fontSize = 16.sp)
                Spacer(Modifier.width(24.dp))
                SourceButton("Settings", onClick = onOpenSettings)
            }

            error?.let {
                Text("Error: $it", color = Color(0xFFFF6666), fontSize = 16.sp)
            }

            Text("Most recent", color = Color.White, fontSize = 20.sp)
            SourceButton(
                "▶ Play slideshow of most recent photos",
                modifier = Modifier.focusRequester(focusRecent),
                onClick = { onStartSlideshow("recent", "") },
            )

            Text("Albums", color = Color.White, fontSize = 20.sp)
            if (albums.isEmpty()) {
                Text("No albums found.", color = Color(0xFF888888), fontSize = 14.sp)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(albums, key = { it.id }) { a ->
                        SourceButton(
                            "${a.name}  (${a.assetCount})",
                            onClick = { onStartSlideshow("album", a.id) },
                        )
                    }
                }
            }

            Text("Folders", color = Color.White, fontSize = 20.sp)
            if (folders.isEmpty()) {
                Text("No folders.", color = Color(0xFF888888), fontSize = 14.sp)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(folders, key = { it }) { f ->
                        SourceButton(f, onClick = { onStartSlideshow("folder", f) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color(0xFFFF6600) else Color(0xFF1E1E1E))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick(); true
                } else false
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(label, color = Color.White, fontSize = 16.sp)
    }
}
