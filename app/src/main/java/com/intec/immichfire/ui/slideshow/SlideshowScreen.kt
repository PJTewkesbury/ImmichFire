package com.intec.immichfire.ui.slideshow

import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.intec.immichfire.data.FitMode
import com.intec.immichfire.data.ImmichSettings
import com.intec.immichfire.data.SettingsRepository
import com.intec.immichfire.data.Transition
import com.intec.immichfire.network.Asset
import com.intec.immichfire.network.ImmichClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SlideshowScreen(
    repo: SettingsRepository,
    source: String,
    param: String,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val settings by repo.settings.collectAsState(initial = ImmichSettings())
    val uiScope = rememberCoroutineScope()
    val vm: SlideshowViewModel = viewModel(
        key = "slideshow-$source-$param",
        factory = SlideshowViewModel.Factory(context.applicationContext, source, param),
    )
    val state by vm.state.collectAsState()

    LaunchedEffect(settings.serverUrl, settings.apiKey, settings.shuffle) {
        vm.load(settings.serverUrl, settings.apiKey, settings.shuffle)
    }

    DisposableEffect(state.playing) {
        val window = (context as? androidx.activity.ComponentActivity)?.window
        if (state.playing) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(state.playing, state.index, settings.slideshowIntervalMs, state.assets.size) {
        if (state.playing && state.assets.isNotEmpty()) {
            delay(settings.slideshowIntervalMs)
            vm.next(settings.loop)
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    var showControls by remember { mutableStateOf(false) }
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4_000)
            showControls = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight, Key.MediaNext -> { vm.next(settings.loop); true }
                    Key.DirectionLeft, Key.MediaPrevious -> { vm.prev(settings.loop); true }
                    Key.DirectionCenter, Key.Enter, Key.Spacebar,
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { vm.togglePlay(); true }
                    Key.DirectionUp -> { vm.toggleInfo(); true }
                    Key.DirectionDown -> { showControls = !showControls; true }
                    Key.Back, Key.Escape -> { onExit(); true }
                    else -> false
                }
            },
    ) {
        when {
            state.loading -> Centered("Loading…", Color.White)
            state.error != null -> Centered("Error: ${state.error}", Color(0xFFFF6666))
            state.assets.isEmpty() -> Centered("No images found.", Color.White)
            else -> {
                val asset = state.assets[state.index]
                AnimatedContent(
                    targetState = state.index,
                    transitionSpec = { transitionFor(settings.transition) },
                    label = "slideshow",
                    modifier = Modifier.fillMaxSize(),
                ) { i ->
                    val a = state.assets.getOrNull(i) ?: return@AnimatedContent
                    val u = ImmichClient.thumbnailUrl(settings.serverUrl, a.id, size = "preview")
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(u)
                            .httpHeaders(NetworkHeaders.Builder().set("x-api-key", settings.apiKey).build())
                            .build(),
                        contentDescription = a.originalFileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = when (settings.fitMode) {
                            FitMode.Fit -> ContentScale.Fit
                            FitMode.Fill -> ContentScale.Crop
                            FitMode.FillBlurBackdrop -> ContentScale.Fit
                        },
                    )
                }

                if (state.showInfo) {
                    Column(
                        Modifier
                            .align(Alignment.TopStart)
                            .background(Color(0xCC000000))
                            .padding(16.dp),
                    ) {
                        Text(asset.originalFileName ?: asset.id, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        asset.fileCreatedAt?.let { Text(it, color = Color(0xFFCCCCCC), fontSize = 14.sp) }
                        Text("${state.index + 1} / ${state.assets.size}", color = Color(0xFFAAAAAA), fontSize = 14.sp)
                    }
                }

                AssetMetaOverlay(
                    asset = asset,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )

                if (!state.playing) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(16.dp)
                            .background(Color(0xCC000000)).padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text("Paused", color = Color.White, fontSize = 14.sp) }
                }

                if (showControls) {
                    Controls(
                        settings = settings,
                        onChangeInterval = { ms -> uiScope.launch { repo.update { it.copy(slideshowIntervalMs = ms) } } },
                        onChangeTransition = { t -> uiScope.launch { repo.update { it.copy(transition = t) } } },
                        onToggleShuffle = { uiScope.launch { repo.update { it.copy(shuffle = !it.shuffle) } } },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Centered(msg: String, color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(msg, color = color, fontSize = 20.sp)
    }
}

private fun transitionFor(t: Transition): ContentTransform = when (t) {
    Transition.None -> fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    Transition.Crossfade -> fadeIn(tween(600)) togetherWith fadeOut(tween(600))
    Transition.Slide -> (slideInHorizontally(tween(500)) { it } + fadeIn(tween(500))) togetherWith
        (slideOutHorizontally(tween(500)) { -it } + fadeOut(tween(500)))
    Transition.KenBurns -> fadeIn(tween(1200)) togetherWith fadeOut(tween(1200))
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Controls(
    settings: ImmichSettings,
    onChangeInterval: (Long) -> Unit,
    onChangeTransition: (Transition) -> Unit,
    onToggleShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Speed:", color = Color(0xFFAAAAAA), fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        listOf(3_000L, 5_000L, 10_000L, 30_000L, 60_000L).forEach { ms ->
            Chip("${ms / 1000}s", selected = settings.slideshowIntervalMs == ms, onClick = { onChangeInterval(ms) })
        }
        Spacer(Modifier.width(24.dp))
        Text("Transition:", color = Color(0xFFAAAAAA), fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Transition.entries.forEach { t ->
            Chip(t.name, selected = settings.transition == t, onClick = { onChangeTransition(t) })
        }
        Spacer(Modifier.width(24.dp))
        Chip(if (settings.shuffle) "Shuffle: on" else "Shuffle: off", selected = settings.shuffle, onClick = onToggleShuffle)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .background(
                when {
                    focused -> Color(0xFFFF6600)
                    selected -> Color(0xFF444444)
                    else -> Color(0xFF222222)
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick(); true
                } else false
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) { Text(label, color = Color.White, fontSize = 13.sp) }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AssetMetaOverlay(asset: Asset, modifier: Modifier = Modifier) {
    val dateText = formatAssetDate(asset)
    val locality = listOfNotNull(
        asset.exifInfo?.city,
        asset.exifInfo?.state,
        asset.exifInfo?.country,
    ).joinToString(", ").takeIf { it.isNotBlank() }
    val rating = asset.exifInfo?.rating?.takeIf { it in 1..5 }

    if (dateText == null && locality == null && rating == null) return

    Column(
        modifier.background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        dateText?.let { Text(it, color = Color.White, fontSize = 14.sp) }
        locality?.let { Text(it, color = Color(0xFFCCCCCC), fontSize = 13.sp) }
        rating?.let {
            Text("★".repeat(it) + "☆".repeat(5 - it), color = Color(0xFFFFC107), fontSize = 14.sp)
        }
    }
}

private fun formatAssetDate(asset: Asset): String? {
    val raw = asset.exifInfo?.dateTimeOriginal
        ?: asset.localDateTime
        ?: asset.fileCreatedAt
        ?: return null
    return runCatching {
        val odt = java.time.OffsetDateTime.parse(raw)
        odt.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
    }.getOrElse {
        runCatching {
            java.time.LocalDateTime.parse(raw.removeSuffix("Z"))
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
        }.getOrNull() ?: raw.take(10)
    }
}
