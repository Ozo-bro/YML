package com.korenskoy.yandexmusic.presentation.hifi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

/**
 * The background of every page: one solid ground (flat design, no cover blur, glow, vignette or
 * grain). The parameters are kept for the callers' API.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun HiFiBackdrop(
    modifier: Modifier = Modifier,
    tint: ArtworkTint = LocalArtworkTint.current,
    coverUrl: String? = null,
    glowCenter: Offset = Offset(0.5f, 0.24f),
    intensity: Float = 1f,
    bottomFade: Boolean = true
) {
    Box(modifier = modifier.fillMaxSize().background(HiFi.Ground))
}
