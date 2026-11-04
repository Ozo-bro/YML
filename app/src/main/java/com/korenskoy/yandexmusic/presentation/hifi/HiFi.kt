package com.korenskoy.yandexmusic.presentation.hifi

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's design language, flat dark (the same as the car screen): a solid ground, solid surfaces
 * without glass, metal, gradients or shadows, one blue accent. Every screen takes its colors from here.
 */
object HiFi {
    val Ground = Color(0xFF0E1013)
    /** Cards, buttons, bars. */
    val Surface = Color(0xFF1A1D22)
    /** Something on a surface (a track of a slider, a selected tab). */
    val SurfaceHigh = Color(0xFF262A31)
    /** The selected/"on" fill of a control. */
    val SelectedFill = Color(0x383D8BFF)
    val Accent = Color(0xFF3D8BFF)
    /** A liked track's heart. */
    val Like = Color(0xFFFF4D6D)
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.66f)
    val TextTertiary = Color.White.copy(alpha = 0.55f)
    val Hairline = Color.White.copy(alpha = 0.10f)
    val GlassFill = Surface
    val GlassBorder = Color.Transparent
    val BarFill = Surface
    val SheetFill = Color(0xFF15171B)
    val Danger = Color(0xFFFF6B6B)
}

/** Four speeds and one curve for the whole app (board "Переходы и плавность"). */
object HiFiMotion {
    const val QUICK = 120
    const val STANDARD = 220
    const val LARGE = 350
    /** Background tint follows a new cover slowly so the change is barely noticed. */
    const val TINT = 700

    val Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> quick(): TweenSpec<T> = tween(QUICK, easing = Easing)
    fun <T> standard(): TweenSpec<T> = tween(STANDARD, easing = Easing)
    fun <T> large(): TweenSpec<T> = tween(LARGE, easing = Easing)
    fun <T> tint(): TweenSpec<T> = tween(TINT, easing = Easing)
    fun <T> spring(): SpringSpec<T> = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
}

/** The app-wide tint taken from the current cover. */
@Immutable
data class ArtworkTint(val primary: Color, val secondary: Color) {
    companion object {
        /** Nothing playing: neutral graphite. */
        val Neutral = ArtworkTint(Color(0xFF3A4152), Color(0xFF1C2130))
    }
}

val LocalArtworkTint = compositionLocalOf { ArtworkTint.Neutral }
