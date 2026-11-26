package com.intec.immichfire.ui.sources

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.intec.immichfire.ui.theme.ImmichColors
import com.intec.immichfire.ui.theme.ImmichLogo
import com.intec.immichfire.ui.theme.ImmichSectionCard
import com.intec.immichfire.ui.theme.ImmichRowButton

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
    var refreshTick by remember { mutableIntStateOf(0) }

    // Refresh the album list every time the app returns to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(settings.serverUrl, settings.apiKey, refreshTick) {
        if (settings.isConfigured) {
            error = null
            runCatching {
                val api = ImmichClient.create(settings.serverUrl, settings.apiKey)
                albums = api.listAlbums()
                    .filter { it.assetCount > 0 && it.name.isNotBlank() }
                    .sortedByDescending { it.createdAt ?: "" }
                folders = runCatching { api.listFolderPaths() }.getOrDefault(emptyList())
            }.onFailure { error = it.message }
        }
    }

    val focusRecent = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRecent.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(ImmichColors.Background)
            .padding(horizontal = 36.dp, vertical = 20.dp),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header bar — Immich logo + server + settings pill
            Row(verticalAlignment = Alignment.CenterVertically) {
                ImmichLogo()
                Spacer(Modifier.width(16.dp))
                Text(
                    settings.serverUrl,
                    color = ImmichColors.OnSurfaceSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.width(1.dp).weight(1f))
                HeaderPill("Settings", onClick = onOpenSettings)
            }

            error?.let {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFEAEA))
                        .border(1.dp, Color(0xFFF2B5B5), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("Error: $it", color = ImmichColors.Error, fontSize = 13.sp)
                }
            }

            // "Most recent" section card
            ImmichSectionCard(
                title = "Most recent",
                subtitle = "Play a slideshow of your newest photos",
            ) {
                ImmichRowButton(
                    label = "Play slideshow of most recent photos",
                    modifier = Modifier.focusRequester(focusRecent),
                    onClick = { onStartSlideshow("recent", "") },
                )
            }

            // Albums card — gets the remaining vertical space so more rows show
            ImmichSectionCard(
                title = "Albums",
                subtitle = "Collections of assets that can be shared with other users.",
            ) {
                if (albums.isEmpty()) {
                    Text(
                        "No albums found.",
                        color = ImmichColors.OnSurfaceSecondary,
                        fontSize = 12.sp,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(albums, key = { it.id }) { a ->
                            ImmichRowButton(
                                label = a.name,
                                trailing = "${a.assetCount}",
                                onClick = { onStartSlideshow("album", a.id) },
                            )
                        }
                    }
                }
            }

            // Folders card
            ImmichSectionCard(
                title = "Folders",
                subtitle = "Browsing the folder view for the photos and videos on the file system",
            ) {
                if (folders.isEmpty()) {
                    Text(
                        "No folders.",
                        color = ImmichColors.OnSurfaceSecondary,
                        fontSize = 12.sp,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(folders, key = { it }) { f ->
                            ImmichRowButton(label = f, onClick = { onStartSlideshow("folder", f) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderPill(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) ImmichColors.Primary else ImmichColors.Surface)
            .border(
                width = 1.dp,
                color = if (focused) ImmichColors.PrimaryFocus else ImmichColors.PrimaryBorder,
                shape = RoundedCornerShape(20.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick(); true
                } else false
            }
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (focused) Color.White else ImmichColors.Primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
