package com.korenskoy.yandexmusic.presentation.hifi

import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * The "knob": a black anodized disc with a metal rim and fine concentric grooves, like the controls of
 * premium audio hardware. Presses in to 0.96, springs back and gives a short haptic tick.
 * Everything static is cached (drawWithCache); the press only changes a layer scale — no redraw.
 */
@Composable
fun HiFiKnob(
    onClick: () -> Unit,
    contentDescription: String,
    size: Dp,
    modifier: Modifier = Modifier,
    /** The main play button: white with a dark glyph (pass the glyph tint [HiFi.Ground]). */
    primary: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = if (pressed) tween(80, easing = HiFiMotion.Easing) else HiFiMotion.spring(),
        label = "knob_press"
    )
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // Flat: a solid round surface
            .clip(CircleShape)
            .background(if (primary) Color.White else HiFi.Surface)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Play ↔ Pause glyph that cross-fades (120 ms) instead of jumping; play is optically nudged right. */
@Composable
fun PlayPauseGlyph(isPlaying: Boolean, size: Dp, tint: Color = Color(0xFFF4F6FA)) {
    Crossfade(targetState = isPlaying, animationSpec = HiFiMotion.quick(), label = "play_pause") { playing ->
        Icon(
            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(size)
                .offset(x = if (playing) 0.dp else size * 0.05f)
        )
    }
}

enum class RingMode { Playing, Paused, Loading }

/**
 * The track progress as a ring around a round button: a dim track and a bright arc for the played
 * part, redrawn only when the progress changes. [mode] and [bars] are kept for the callers' API.
 */
@Composable
fun SpectrumRing(
    mode: RingMode,
    progress: () -> Float,
    bars: Int,
    innerRadius: Dp,
    maxLength: Dp,
    modifier: Modifier = Modifier,
    barWidth: Dp = 3.dp,
    playedColor: Color = Color.White.copy(alpha = 0.95f),
    restColor: Color = Color.White.copy(alpha = 0.26f)
) {
    Canvas(modifier = modifier) {
        val radius = innerRadius.toPx() + maxLength.toPx() / 2f
        val stroke = barWidth.toPx() * 1.6f
        val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
        val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
        drawCircle(color = restColor, radius = radius, style = Stroke(width = stroke))
        val played = progress().coerceIn(0f, 1f)
        if (played > 0f) {
            drawArc(
                color = playedColor,
                startAngle = -90f,
                sweepAngle = 360f * played,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}
