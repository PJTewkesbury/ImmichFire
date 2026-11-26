package com.intec.immichfire.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.intec.immichfire.R

/** Immich-style word-mark: real 5-petal flower + TV badge mark + "immich" in brand purple. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ImmichLogo(size: Int = 36) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_immich_mark),
            contentDescription = null,
            modifier = Modifier.size(size.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Image Fire",
            color = ImmichColors.Primary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** White card with soft purple border, section title in brand purple, optional subtitle, slot for content. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ImmichSectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ImmichColors.Surface)
            .border(1.dp, ImmichColors.PrimaryBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                color = ImmichColors.Primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle?.let {
                Text(it, color = ImmichColors.OnSurfaceSecondary, fontSize = 12.sp)
            }
            content()
        }
    }
}

/** Light pill/button row. Fills width, left label + optional trailing text, focus brings Immich purple. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ImmichRowButton(
    label: String,
    trailing: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) ImmichColors.Primary else ImmichColors.PrimarySubtle)
            .border(
                1.dp,
                if (focused) ImmichColors.PrimaryFocus else ImmichColors.PrimaryBorder,
                RoundedCornerShape(10.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick(); true
                } else false
            }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (focused) Color.White else ImmichColors.OnSurface,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                trailing,
                color = if (focused) Color.White else ImmichColors.OnSurfaceSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

/** Compact value chip used in setting rows (speed, transition, fit). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ImmichChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> ImmichColors.Primary
        selected -> ImmichColors.PrimarySubtle
        else -> ImmichColors.Surface
    }
    val borderColor = when {
        focused -> ImmichColors.PrimaryFocus
        selected -> ImmichColors.Primary
        else -> ImmichColors.PrimaryBorder
    }
    val textColor = when {
        focused -> Color.White
        selected -> ImmichColors.Primary
        else -> ImmichColors.OnSurface
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick(); true
                } else false
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** Immich-style toggle (pill with sliding thumb). Centre/Enter toggles. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ImmichToggle(on: Boolean, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val track = if (on) ImmichColors.Primary else Color(0xFFBFBFBF)
    Box(
        Modifier
            .size(width = 52.dp, height = 28.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .border(
                2.dp,
                if (focused) ImmichColors.PrimaryFocus else Color.Transparent,
                RoundedCornerShape(50),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onToggle(); true
                } else false
            },
    ) {
        Box(
            Modifier
                .padding(start = if (on) 26.dp else 2.dp, top = 2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
