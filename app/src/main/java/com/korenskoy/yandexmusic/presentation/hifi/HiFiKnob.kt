package com.korenskoy.yandexmusic.presentation.hifi

import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

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
    val grooves = size >= 56.dp
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // Flat: a solid round surface
            .clip(CircleShape)
            .background(HiFi.Surface)
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

/** Bar lengths of the spectrum ring. Pure and deterministic so the look is unit-tested. */
object SpectrumMath {
    /** Length 0..1 of bar [i] of [n] at time [t] seconds. Paused bars collapse to dots (0). */
    fun level(i: Int, n: Int, t: Float, mode: RingMode): Float = when (mode) {
        RingMode.Paused -> 0f
        RingMode.Playing -> {
            val base = 0.5f + 0.5f * sin(i * 0.42f + t * 2.2f)
            val envelope = 0.55f + 0.45f * sin(i * 0.13f + t * 0.9f + 2.1f)
            val jitter = abs(sin(i * 1.7f + t * 3.1f)) * 0.18f
            (base * base * envelope * 0.82f + jitter).coerceIn(0.08f, 1f)
        }
        RingMode.Loading -> {
            // A bright wave running around the ring
            val head = (t * 0.6f) % 1f
            var d = abs(i.toFloat() / n - head)
            if (d > 0.5f) d = 1f - d
            (0.06f + 0.94f * exp(-(d * d) / 0.006f)).coerceIn(0f, 1f)
        }
    }

    /** Whether bar [i] of [n] lies in the already played arc (clockwise from the top). */
    fun isPlayed(i: Int, n: Int, progress: Float): Boolean = i.toFloat() / n < progress
}

/**
 * A ring of [bars] strokes around a knob: an equalizer and the track progress in one. One Canvas draw
 * per frame; the clock only runs while playing or loading (and never in light mode).
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
    val lite = LocalHiFiLite.current
    val animate = !lite && mode != RingMode.Paused
    val clock: State<Float> = if (animate) {
        val transition = rememberInfiniteTransition(label = "spectrum")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 3600f,
            animationSpec = infiniteRepeatable(tween(3_600_000, easing = LinearEasing)),
            label = "spectrum_clock"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    if (lite) {
        // Light mode: a frozen equalizer looks broken, so a clean progress ring instead — a dim track
        // and a bright arc for the played part (redrawn only when the progress changes)
        Canvas(modifier = modifier) {
            val radius = innerRadius.toPx() + maxLength.toPx() / 2f
            val stroke = barWidth.toPx() * 1.6f
            val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
            val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
            drawCircle(color = restColor, radius = radius, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
            val played = progress().coerceIn(0f, 1f)
            if (played > 0f) {
                drawArc(
                    color = playedColor,
                    startAngle = -90f,
                    sweepAngle = 360f * played,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        return
    }
    Canvas(modifier = modifier) {
        val t = clock.value
        val c = Offset(size.width / 2f, size.height / 2f)
        val inner = innerRadius.toPx()
        val maxLen = maxLength.toPx()
        val stroke = barWidth.toPx()
        val played = progress().coerceIn(0f, 1f)
        for (i in 0 until bars) {
            val angle = (i.toFloat() / bars) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val dx = cos(angle)
            val dy = sin(angle)
            val len = SpectrumMath.level(i, bars, t, mode) * maxLen
            val start = Offset(c.x + dx * inner, c.y + dy * inner)
            val end = Offset(c.x + dx * (inner + len), c.y + dy * (inner + len))
            drawLine(
                color = if (SpectrumMath.isPlayed(i, bars, played)) playedColor else restColor,
                start = start,
                end = end,
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}
