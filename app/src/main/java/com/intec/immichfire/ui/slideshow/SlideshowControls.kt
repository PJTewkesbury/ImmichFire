package com.intec.immichfire.ui.slideshow

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.intec.immichfire.core.Constants
import com.intec.immichfire.data.ImmichSettings
import com.intec.immichfire.data.SortOrder
import com.intec.immichfire.data.Transition

/** Full-width overlay at the bottom of the slideshow with speed / transition / sort / shuffle. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun Controls(
    settings: ImmichSettings,
    firstChipFocusRequester: FocusRequester,
    onInteraction: () -> Unit,
    onChangeInterval: (Long) -> Unit,
    onChangeTransition: (Transition) -> Unit,
    onChangeSortOrder: (SortOrder) -> Unit,
    onToggleShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Speed:", color = Color(0xFFAAAAAA), fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Constants.Slideshow.INTERVAL_MS_CHOICES
                // The Settings screen shows every choice; the in-slideshow overlay keeps the
                // short options only so the row doesn't wrap on narrower TVs.
                .filter { it <= 60_000L }
                .forEachIndexed { i, ms ->
                    Chip(
                        label = "${ms / 1000}s",
                        selected = settings.slideshowIntervalMs == ms,
                        onFocused = onInteraction,
                        onClick = { onChangeInterval(ms) },
                        modifier = if (i == 0) Modifier.focusRequester(firstChipFocusRequester) else Modifier,
                    )
                }
            Spacer(Modifier.width(24.dp))
            Text("Transition:", color = Color(0xFFAAAAAA), fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Transition.entries.forEach { t ->
                Chip(
                    label = t.name,
                    selected = settings.transition == t,
                    onFocused = onInteraction,
                    onClick = { onChangeTransition(t) },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Order:", color = Color(0xFFAAAAAA), fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Chip(
                label = "Newest first",
                selected = settings.sortOrder == SortOrder.NewestFirst,
                onFocused = onInteraction,
                onClick = { onChangeSortOrder(SortOrder.NewestFirst) },
            )
            Chip(
                label = "Oldest first",
                selected = settings.sortOrder == SortOrder.OldestFirst,
                onFocused = onInteraction,
                onClick = { onChangeSortOrder(SortOrder.OldestFirst) },
            )
            Spacer(Modifier.width(24.dp))
            ToggleChip(
                label = "Shuffle",
                on = settings.shuffle,
                onFocused = onInteraction,
                onToggle = onToggleShuffle,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun Centered(msg: String, color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(msg, color = color, fontSize = 20.sp)
    }
}

internal fun transitionFor(t: Transition): ContentTransform = when (t) {
    Transition.None -> fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    Transition.Crossfade -> fadeIn(tween(600)) togetherWith fadeOut(tween(600))
    Transition.Slide -> (slideInHorizontally(tween(500)) { it } + fadeIn(tween(500))) togetherWith
        (slideOutHorizontally(tween(500)) { -it } + fadeOut(tween(500)))
    Transition.KenBurns -> fadeIn(tween(900)) togetherWith fadeOut(tween(900))
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    focused -> Color(0xFFFF6600)
                    selected -> Color(0xFF444444)
                    else -> Color(0xFF222222)
                }
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick(); true
                } else false
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) { Text(label, color = Color.White, fontSize = 13.sp) }
}

/** Overlay toggle chip with a ☑/☐ indicator so it reads as a toggle, not an exclusive-select chip. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ToggleChip(
    label: String,
    on: Boolean,
    onToggle: () -> Unit,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    focused -> Color(0xFFFF6600)
                    on -> Color(0xFF2E7D32)
                    else -> Color(0xFF222222)
                }
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onToggle(); true
                } else false
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            (if (on) "☑ " else "☐ ") + label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
