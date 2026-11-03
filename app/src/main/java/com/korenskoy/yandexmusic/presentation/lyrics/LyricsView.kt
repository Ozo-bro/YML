package com.korenskoy.yandexmusic.presentation.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.korenskoy.yandexmusic.data.lyrics.Lyrics
import com.korenskoy.yandexmusic.data.lyrics.LyricsParser

/**
 * Lyrics that follow the song: the line being sung is bright and kept a third of the way down, the
 * rest dimmed. [progress] is read in a derived state, so only a change of line recomposes.
 * Plain (unsynced) lyrics are just a scrollable text.
 */
@Composable
fun LyricsView(
    lyrics: Lyrics,
    progress: () -> Long,
    textSize: TextUnit,
    lineSpacing: Dp,
    modifier: Modifier = Modifier
) {
    when (lyrics) {
        is Lyrics.Plain -> Text(
            lyrics.text,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = textSize * 0.8f,
            fontWeight = FontWeight.SemiBold,
            modifier = modifier.verticalScroll(rememberScrollState())
        )
        is Lyrics.Synced -> BoxWithConstraints(modifier) {
            val lines = lyrics.lines
            // A little ahead: the line lights up as it starts, not after
            val current by remember(lines) { derivedStateOf { LyricsParser.currentIndex(lines, progress() + 250) } }
            val listState = rememberLazyListState()
            LaunchedEffect(current) {
                if (current >= 0) listState.animateScrollToItem(current)
            }
            LazyColumn(
                state = listState,
                userScrollEnabled = false,
                contentPadding = PaddingValues(top = maxHeight / 3, bottom = maxHeight * 2 / 3),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(lines) { index, line ->
                    val target = when {
                        index == current -> Color.White
                        index < current -> Color.White.copy(alpha = 0.32f)
                        else -> Color.White.copy(alpha = 0.50f)
                    }
                    val color by animateColorAsState(target, label = "lyric_line")
                    Text(
                        line.text.ifBlank { "♪" },
                        color = color,
                        fontSize = textSize,
                        lineHeight = textSize * 1.2f,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.fillMaxWidth().padding(vertical = lineSpacing)
                    )
                }
            }
        }
    }
}
