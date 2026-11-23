package com.korenskoy.yandexmusic.presentation.main.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.domain.model.WaveDiversity
import com.korenskoy.yandexmusic.domain.model.WaveLanguage
import com.korenskoy.yandexmusic.domain.model.WaveMood
import com.korenskoy.yandexmusic.domain.model.WaveTuning
import com.korenskoy.yandexmusic.presentation.hifi.HiFi

@Composable
private fun WaveDiversity.label(): String = stringResource(
    when (this) {
        WaveDiversity.FAVORITE -> R.string.wave_diversity_favorite
        WaveDiversity.DISCOVER -> R.string.wave_diversity_discover
        WaveDiversity.POPULAR -> R.string.wave_diversity_popular
        WaveDiversity.ANY -> R.string.wave_any_neuter
    }
)

@Composable
private fun WaveMood.label(): String = stringResource(
    when (this) {
        WaveMood.ACTIVE -> R.string.wave_mood_active
        WaveMood.FUN -> R.string.wave_mood_fun
        WaveMood.CALM -> R.string.wave_mood_calm
        WaveMood.SAD -> R.string.wave_mood_sad
        WaveMood.ANY -> R.string.wave_any_neuter
    }
)

@Composable
private fun WaveLanguage.label(): String = stringResource(
    when (this) {
        WaveLanguage.RUSSIAN -> R.string.wave_language_russian
        WaveLanguage.FOREIGN -> R.string.wave_language_foreign
        WaveLanguage.NO_WORDS -> R.string.wave_language_no_words
        WaveLanguage.ANY -> R.string.wave_any_masc
    }
)

/**
 * Reusable flat-dark My Wave tuning panel (character, mood, language + reset).
 * Used inline inside CarScreen's side panel and inside [WaveTuneOverlay] on the Wave tab.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaveTuneContent(
    tuning: WaveTuning,
    onChange: (WaveTuning) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1A1B20))
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.wave_tune_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (!tuning.isDefault) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(role = Role.Button) { onChange(WaveTuning()) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.wave_tune_reset), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.size(8.dp))
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_zakryt), tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        TuneGroup(stringResource(R.string.wave_tune_diversity)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WaveDiversity.entries.forEach { item ->
                    TuneChip(
                        label = item.label(),
                        selected = tuning.diversity == item,
                        onClick = { onChange(tuning.copy(diversity = item)) }
                    )
                }
            }
        }

        TuneGroup(stringResource(R.string.wave_tune_mood)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WaveMood.entries.forEach { item ->
                    TuneChip(
                        label = item.label(),
                        selected = tuning.mood == item,
                        onClick = { onChange(tuning.copy(mood = item)) }
                    )
                }
            }
        }

        TuneGroup(stringResource(R.string.wave_tune_language)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WaveLanguage.entries.forEach { item ->
                    TuneChip(
                        label = item.label(),
                        selected = tuning.language == item,
                        onClick = { onChange(tuning.copy(language = item)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TuneGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun TuneChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) HiFi.Accent else Color.White.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.Black else Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** Full-screen dimmed overlay (in-composition, no Dialog window flicker) for the Wave screen. */
@Composable
fun WaveTuneOverlay(
    tuning: WaveTuning,
    onChange: (WaveTuning) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        WaveTuneContent(
            tuning = tuning,
            onChange = onChange,
            onClose = onDismiss,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
        )
    }
}
