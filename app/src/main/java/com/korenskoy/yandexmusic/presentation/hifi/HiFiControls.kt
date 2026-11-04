package com.korenskoy.yandexmusic.presentation.hifi

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Text styles of the Hi-Fi language. */
object HiFiType {
    val LargeTitle = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.025).em, color = Color.White)
    val SectionCaps = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.1.em, color = HiFi.TextSecondary)
    val RowTitle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    val CardTitle = TextStyle(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
    val Body = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = Color.White)
    val Caption = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, color = HiFi.TextSecondary)
}

/**
 * A flat surface. Kept under its old name: a translucent "glass" fill becomes the solid surface, a
 * dark or coloured fill is kept, and there is no border.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.hifiGlass(shape: Shape, fill: Color = HiFi.GlassFill, border: Color = HiFi.GlassBorder): Modifier =
    this.clip(shape).background(if (fill.alpha < 0.5f) HiFi.Surface else fill)

/** A flat inset (time readouts, slider tracks): the raised surface colour, no shading. */
fun Modifier.hifiRecessed(cornerRadius: Dp): Modifier = this.clip(RoundedCornerShape(cornerRadius)).background(HiFi.SurfaceHigh)

/** The "selected" material: a flat soft-blue fill. */
fun Modifier.hifiAnodized(shape: Shape): Modifier = this.clip(shape).background(HiFi.SelectedFill)

/** Page title, 34/800. */
@Composable
fun HiFiLargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = HiFiType.LargeTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** Section header: small caps with letter-spacing, optional trailing action. */
@Composable
fun HiFiSectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = text.uppercase(), style = HiFiType.SectionCaps, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

/** A quiet text action (≥44dp touch height). */
@Composable
fun HiFiTextAction(text: String, onClick: () -> Unit, color: Color = Color.White.copy(alpha = 0.82f)) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Round glass button (back, close, settings, history). */
@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    // Big enough to hit in a car
    size: Dp = 52.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .hifiGlass(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Primary action: a flat white capsule with dark text. */
@Composable
fun MetalPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 52.dp
) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .height(height)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        PillLabel(text, icon, HiFi.Ground)
    }
}

/** Secondary action: a flat surface capsule. */
@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 52.dp
) {
    Box(
        modifier = modifier
            .height(height)
            .hifiGlass(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        PillLabel(text, icon)
    }
}

@Composable
private fun PillLabel(text: String, icon: ImageVector?, color: Color = Color.White) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Filter/genre pill: anodized when selected, glass otherwise. */
@Composable
fun HiFiFilterPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .height(36.dp)
            .then(if (selected) Modifier.hifiAnodized(shape) else Modifier.hifiGlass(shape, fill = Color.White.copy(alpha = 0.06f), border = Color.White.copy(alpha = 0.14f)))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.86f),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** A small metal plaque behind a row's icon. */
@Composable
fun IconPlaque(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 34.dp, tint: Color = Color.White, danger: Boolean = false) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(if (danger) HiFi.Danger.copy(alpha = 0.15f) else HiFi.SurfaceHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) HiFi.Danger else tint, modifier = Modifier.size(size * 0.56f))
    }
}

/** Segmented control: a dark track with a raised piece sliding under the selected option (as the car tabs). */
@Composable
fun HiFiSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(HiFi.Ground)
            .padding(4.dp)
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selectedIndex, HiFiMotion.spring(), label = "segment")
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(HiFi.SurfaceHigh)
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(19.dp))
                        .selectable(selected = selected, role = Role.RadioButton) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.70f),
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** A hardware-like toggle: blue track when on, a bright metal thumb. */
@Composable
fun HiFiSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val track by animateColorAsState(if (checked) HiFi.Accent else HiFi.SurfaceHigh, HiFiMotion.quick(), label = "switch_track")
    val thumbX by animateDpAsState(if (checked) 24.dp else 2.dp, HiFiMotion.spring(), label = "switch_thumb")
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(width = 54.dp, height = 32.dp)
            .clip(CircleShape)
            .background(track)
            .toggleable(value = checked, role = Role.Switch, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onCheckedChange(it)
            }
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbX.roundToPx(), 2.dp.roundToPx()) }
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** Slider with a recessed track, white filled part and a small metal knob. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiFiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interaction,
        modifier = modifier,
        thumb = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        },
        track = { state ->
            val fraction = ((state.value - state.valueRange.start) / (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .hifiRecessed(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f))
                )
            }
        },
        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
    )
}
