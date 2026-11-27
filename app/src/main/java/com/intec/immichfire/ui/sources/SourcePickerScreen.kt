package com.intec.immichfire.ui.sources

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.intec.immichfire.core.appContainer
import com.intec.immichfire.data.ImmichSettings
import com.intec.immichfire.ui.theme.ImmichColors
import com.intec.immichfire.ui.theme.ImmichLogo
import com.intec.immichfire.ui.theme.ImmichRowButton
import com.intec.immichfire.ui.theme.ImmichSectionCard

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SourcePickerScreen(
    onStartSlideshow: (source: String, param: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val settings by container.settingsRepository.settings.collectAsState(initial = ImmichSettings())

    val vm: SourcePickerViewModel = viewModel(
        factory = SourcePickerViewModel.Factory(container.immichRepository),
    )
    val state by vm.state.collectAsState()

    // Refresh on first entry AND on every ON_RESUME (user coming back from Settings etc.).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, settings.serverUrl, settings.apiKey) {
        if (settings.isConfigured) vm.refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && settings.isConfigured) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val focusRecent = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRecent.requestFocus() } }

    // Double-tap-back to exit so a stray Back doesn't drop out of the app silently.
    var lastBackPressAt by remember { mutableLongStateOf(0L) }
    BackHandler {
        val now = System.currentTimeMillis()
        if (now - lastBackPressAt < 2_000L) {
            (context as? ComponentActivity)?.finish()
        } else {
            lastBackPressAt = now
            Toast.makeText(context, "Press Back again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(ImmichColors.Background)
            .padding(horizontal = 36.dp, vertical = 20.dp),
    ) {
        // Single outer LazyColumn virtualises the album/folder rows, so hundreds of albums
        // don't force every row to compose and measure before paint. Each ROW is its own
        // item; section headers sit above their rows as additional items. Focus traversal
        // works because LazyColumn supports bringIntoView natively.
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "header") {
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
            }

            state.error?.let { err ->
                item(key = "error") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFEAEA))
                            .border(1.dp, Color(0xFFF2B5B5), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("Error: ${err.displayMessage}", color = ImmichColors.Error, fontSize = 13.sp)
                    }
                }
            }

            item(key = "recent") {
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
            }

            item(key = "albums-header") {
                SectionHeader(
                    title = "Albums",
                    subtitle = "Collections of assets that can be shared with other users.",
                    empty = state.albums.isEmpty(),
                    emptyText = "No albums found.",
                )
            }
            items(state.albums, key = { "album-" + it.id }) { a ->
                ImmichRowButton(
                    label = a.name,
                    trailing = "${a.assetCount}",
                    onClick = { onStartSlideshow("album", a.id) },
                )
            }

            item(key = "folders-header") {
                SectionHeader(
                    title = "Folders",
                    subtitle = "Browsing the folder view for the photos and videos on the file system",
                    empty = state.folders.isEmpty(),
                    emptyText = "No folders.",
                )
            }
            items(state.folders, key = { "folder-" + it.path }) { f ->
                ImmichRowButton(
                    label = f.displayName,
                    trailing = f.count?.toString(),
                    onClick = { onStartSlideshow("folder", f.path) },
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionHeader(title: String, subtitle: String, empty: Boolean, emptyText: String) {
    ImmichSectionCard(title = title, subtitle = subtitle) {
        if (empty) {
            Text(emptyText, color = ImmichColors.OnSurfaceSecondary, fontSize = 12.sp)
        } else {
            Spacer(Modifier.size(0.dp))
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
