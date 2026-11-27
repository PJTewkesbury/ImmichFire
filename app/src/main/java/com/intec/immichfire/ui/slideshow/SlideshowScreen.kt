package com.intec.immichfire.ui.slideshow

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.size.Size
import com.intec.immichfire.core.Constants
import com.intec.immichfire.core.appContainer
import com.intec.immichfire.data.FitMode
import com.intec.immichfire.data.ImmichSettings
import com.intec.immichfire.data.SettingsRepository
import com.intec.immichfire.data.Transition
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
        factory = SlideshowViewModel.Factory(context.appContainer.immichRepository, source, param),
    )
    val state by vm.state.collectAsState()

    LaunchedEffect(settings.serverUrl, settings.apiKey, settings.shuffle, settings.sortOrder) {
        vm.load(settings.shuffle, settings.sortOrder)
        vm.resort(settings.sortOrder)
    }

    DisposableEffect(state.playing) {
        val window = (context as? androidx.activity.ComponentActivity)?.window
        if (state.playing) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Coil's LRU memory cache handles its own eviction; wiping it on exit discards warm
    // bitmaps the user might see again seconds later (slideshow re-entry, back from
    // Settings, etc.). Leave it alone.

    LaunchedEffect(state.playing, state.index, settings.slideshowIntervalMs, state.assets.size) {
        if (state.playing && state.assets.isNotEmpty()) {
            delay(settings.slideshowIntervalMs)
            vm.next(settings.loop)
        }
    }

    // Prefetch the next few images into Coil's memory/disk cache so the next
    // transition doesn't start with a blank frame while the network loads. Matches the
    // live slideshow's bitmap config so the prefetched decode hits the memory cache.
    LaunchedEffect(state.index, state.assets.size, settings.serverUrl, settings.apiKey) {
        if (state.assets.isEmpty()) return@LaunchedEffect
        val loader = SingletonImageLoader.get(context)
        val headers = NetworkHeaders.Builder().set("x-api-key", settings.apiKey).build()
        for (offset in 1..Constants.Slideshow.PREFETCH_AHEAD) {
            val next = state.index + offset
            val a = state.assets.getOrNull(next) ?: break
            val u = ImmichClient.thumbnailUrl(settings.serverUrl, a.id, size = "preview")
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(u)
                    .httpHeaders(headers)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .build()
            )
        }
    }

    val focusRequester = remember { FocusRequester() }
    val controlsFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    var showControls by remember { mutableStateOf(false) }
    var controlsInteraction by remember { mutableStateOf(0) }
    LaunchedEffect(showControls, controlsInteraction) {
        if (showControls) {
            delay(Constants.Slideshow.OVERLAY_AUTO_HIDE_MS)
            showControls = false
        }
    }
    LaunchedEffect(showControls) {
        if (showControls) {
            runCatching { controlsFocusRequester.requestFocus() }
        } else {
            runCatching { focusRequester.requestFocus() }
        }
    }

    // Zoom / pan state. zoomMode==false => normal slideshow. On long-press OK we enter zoom
    // mode at 1.5x, pause playback, D-pad pans, short-press OK cycles in 0.5x steps up to 4x,
    // then exits zoom (back to 1x).
    //
    // Long-press detection: schedule a delay() job on KeyDown and cancel it on KeyUp. We
    // used to rely on nativeKeyEvent.repeatCount, but many Fire TV / ADT-3 remotes don't
    // auto-repeat DPAD_CENTER, so long-press never fired.
    var zoomMode by remember { mutableStateOf(false) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var longPressFired by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun clampPan() {
        val maxX = viewportSize.width * (zoomLevel - 1f) / 2f
        val maxY = viewportSize.height * (zoomLevel - 1f) / 2f
        panX = panX.coerceIn(-maxX, maxX)
        panY = panY.coerceIn(-maxY, maxY)
    }

    fun exitZoom() {
        zoomMode = false; zoomLevel = 1f; panX = 0f; panY = 0f
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewportSize = it; clampPan() }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { e ->
                // Overlay up: let focus traversal (Up/Down between chip rows) handle keys;
                // only Back/Escape dismiss.
                if (showControls) {
                    if (e.type == KeyEventType.KeyUp && (e.key == Key.Back || e.key == Key.Escape)) {
                        showControls = false
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }

                // D-pad Center: long-press to enter Zoom, short-press for play/pause or cycle zoom.
                if (e.key == Key.DirectionCenter || e.key == Key.Enter) {
                    when (e.type) {
                        KeyEventType.KeyDown -> {
                            // Only schedule once per press: a KeyDown can repeat on some
                            // remotes even when we don't rely on it for long-press logic.
                            if (longPressJob == null) {
                                longPressFired = false
                                longPressJob = uiScope.launch {
                                    delay(Constants.Slideshow.ZOOM_LONG_PRESS_MS)
                                    longPressFired = true
                                    if (!zoomMode) {
                                        zoomMode = true
                                        zoomLevel = Constants.Slideshow.ZOOM_INITIAL
                                        panX = 0f; panY = 0f
                                        if (state.playing) vm.togglePlay()
                                    }
                                }
                            }
                            return@onKeyEvent true
                        }
                        KeyEventType.KeyUp -> {
                            longPressJob?.cancel()
                            longPressJob = null
                            if (!longPressFired) {
                                if (zoomMode) {
                                    val next = zoomLevel + Constants.Slideshow.ZOOM_STEP
                                    if (next > Constants.Slideshow.ZOOM_MAX + 0.001f) {
                                        exitZoom()
                                    } else {
                                        zoomLevel = next
                                        clampPan()
                                    }
                                } else {
                                    vm.togglePlay()
                                }
                            }
                            longPressFired = false
                            return@onKeyEvent true
                        }
                        else -> return@onKeyEvent false
                    }
                }

                // In Zoom mode, D-pad arrows pan; Back exits zoom; next/prev disabled.
                if (zoomMode) {
                    val step = Constants.Slideshow.ZOOM_PAN_STEP_PX
                    if (e.type == KeyEventType.KeyDown) {
                        when (e.key) {
                            Key.DirectionLeft  -> { panX += step; clampPan(); return@onKeyEvent true }
                            Key.DirectionRight -> { panX -= step; clampPan(); return@onKeyEvent true }
                            Key.DirectionUp    -> { panY += step; clampPan(); return@onKeyEvent true }
                            Key.DirectionDown  -> { panY -= step; clampPan(); return@onKeyEvent true }
                        }
                    }
                    if (e.type == KeyEventType.KeyUp) {
                        when (e.key) {
                            Key.DirectionLeft, Key.DirectionRight,
                            Key.DirectionUp, Key.DirectionDown -> return@onKeyEvent true
                            Key.Back, Key.Escape -> { exitZoom(); return@onKeyEvent true }
                        }
                    }
                    return@onKeyEvent false
                }

                // Normal slideshow controls.
                if (e.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight, Key.MediaNext -> { vm.next(settings.loop); true }
                    Key.DirectionLeft, Key.MediaPrevious -> { vm.prev(settings.loop); true }
                    Key.Spacebar,
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { vm.togglePlay(); true }
                    Key.DirectionUp -> { vm.toggleInfo(); true }
                    Key.DirectionDown -> { showControls = true; true }
                    Key.Back, Key.Escape -> { onExit(); true }
                    else -> false
                }
            },
    ) {
        when {
            state.loading -> Centered("Loading…", Color.White)
            state.error != null -> Centered("Error: ${state.error?.displayMessage}", Color(0xFFFF6666))
            state.assets.isEmpty() -> Centered("No images found.", Color.White)
            else -> {
                val asset = state.assets[state.index]
                // Remember the ContentTransform so AnimatedContent's transitionSpec lambda
                // doesn't rebuild a fresh tween/fadeIn/slideIn spec on each recomposition.
                val cachedTransform = remember(settings.transition) { transitionFor(settings.transition) }
                AnimatedContent(
                    targetState = state.index,
                    transitionSpec = { cachedTransform },
                    label = "slideshow",
                    modifier = Modifier.fillMaxSize(),
                ) { i ->
                    val a = state.assets.getOrNull(i) ?: return@AnimatedContent
                    val u = ImmichClient.thumbnailUrl(settings.serverUrl, a.id, size = "preview")
                    // Foreground: RGB_565 halves the per-slide bitmap cost vs ARGB_8888
                    // (~4 MB → ~2 MB at 1920×1080). The photo variety more than hides the loss.
                    val req = remember(u, settings.apiKey) {
                        ImageRequest.Builder(context)
                            .data(u)
                            .httpHeaders(NetworkHeaders.Builder().set("x-api-key", settings.apiKey).build())
                            .bitmapConfig(Bitmap.Config.RGB_565)
                            .build()
                    }
                    // Backdrop: much smaller request (blurred to oblivion anyway) at 480px max
                    // dimension, also RGB_565. Halves decode cost and GPU upload for the blur pass.
                    val backdropReq = remember(u, settings.apiKey) {
                        ImageRequest.Builder(context)
                            .data(u)
                            .httpHeaders(NetworkHeaders.Builder().set("x-api-key", settings.apiKey).build())
                            .bitmapConfig(Bitmap.Config.RGB_565)
                            .size(Size(480, 480))
                            .build()
                    }

                    // Per-slide Ken Burns animation — each AnimatedContent cell gets its own
                    // Animatables keyed on `i`, so the outgoing slide keeps animating until it
                    // fades out while the incoming slide starts its own pan/zoom from scratch.
                    val kbScale = remember(i) { Animatable(Constants.Slideshow.KEN_BURNS_SCALE_FROM) }
                    val kbTx = remember(i) { Animatable(0f) }
                    val kbTy = remember(i) { Animatable(0f) }
                    LaunchedEffect(i, settings.transition, settings.slideshowIntervalMs) {
                        if (settings.transition == Transition.KenBurns) {
                            // Target over 2x the slide duration, so motion is still visible
                            // while the next slide is crossfading in.
                            val durMs = (settings.slideshowIntervalMs * 2).toInt()
                                .coerceAtLeast(Constants.Slideshow.KEN_BURNS_MIN_DURATION_MS)
                            val tx = Constants.Slideshow.KEN_BURNS_TRANSLATE_FRAC
                            val ty = Constants.Slideshow.KEN_BURNS_TRANSLATE_FRAC_Y
                            val (txTargetFrac, tyTargetFrac) = when (i % 4) {
                                0 -> tx to ty       // pan toward bottom-right
                                1 -> -tx to ty      // pan toward bottom-left
                                2 -> tx to -ty      // pan toward top-right
                                else -> -tx to -ty  // pan toward top-left
                            }
                            launch { kbScale.animateTo(Constants.Slideshow.KEN_BURNS_SCALE_TO, tween(durMs, easing = LinearEasing)) }
                            launch { kbTx.animateTo(txTargetFrac, tween(durMs, easing = LinearEasing)) }
                            launch { kbTy.animateTo(tyTargetFrac, tween(durMs, easing = LinearEasing)) }
                        }
                    }

                    val isCurrent = i == state.index
                    val kenBurnsOn = settings.transition == Transition.KenBurns && !zoomMode
                    val applyZoom = isCurrent && zoomMode

                    val useBlurBackdrop = settings.fitMode == FitMode.FillBlurBackdrop &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

                    Box(Modifier.fillMaxSize()) {
                        if (useBlurBackdrop) {
                            AsyncImage(
                                model = backdropReq,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    // 20dp is visually indistinguishable from 40dp at TV
                                    // viewing distance and halves the GPU blur cost.
                                    .blur(20.dp)
                                    .graphicsLayer(scaleX = 1.1f, scaleY = 1.1f),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        AsyncImage(
                            model = req,
                            contentDescription = a.originalFileName,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val s = when {
                                        applyZoom -> zoomLevel
                                        kenBurnsOn -> kbScale.value
                                        else -> 1f
                                    }
                                    scaleX = s
                                    scaleY = s
                                    translationX = when {
                                        applyZoom -> panX
                                        kenBurnsOn -> kbTx.value * viewportSize.width
                                        else -> 0f
                                    }
                                    translationY = when {
                                        applyZoom -> panY
                                        kenBurnsOn -> kbTy.value * viewportSize.height
                                        else -> 0f
                                    }
                                },
                            contentScale = when (settings.fitMode) {
                                FitMode.Fit -> ContentScale.Fit
                                FitMode.Fill -> ContentScale.Crop
                                FitMode.FillBlurBackdrop -> ContentScale.Fit
                            },
                        )
                    }
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
                        Text("D-pad Up to hide", color = Color(0xFF888888), fontSize = 12.sp)
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

                if (zoomMode) {
                    Column(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .background(Color(0xCC000000))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Zoom ${"%.1f".format(zoomLevel)}×",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "D-pad to pan   ·   OK to cycle zoom   ·   Back to exit",
                            color = Color(0xFFCCCCCC),
                            fontSize = 13.sp,
                        )
                    }
                }

                if (showControls) {
                    Controls(
                        settings = settings,
                        firstChipFocusRequester = controlsFocusRequester,
                        onInteraction = { controlsInteraction++ },
                        onChangeInterval = { ms ->
                            controlsInteraction++
                            uiScope.launch { repo.update { it.copy(slideshowIntervalMs = ms) } }
                        },
                        onChangeTransition = { t ->
                            controlsInteraction++
                            uiScope.launch { repo.update { it.copy(transition = t) } }
                        },
                        onChangeSortOrder = { o ->
                            controlsInteraction++
                            uiScope.launch { repo.update { it.copy(sortOrder = o) } }
                        },
                        onToggleShuffle = {
                            controlsInteraction++
                            uiScope.launch { repo.update { it.copy(shuffle = !it.shuffle) } }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}
