package com.korenskoy.yandexmusic.presentation.car

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.data.lyrics.Lyrics
import com.korenskoy.yandexmusic.data.player.PlaybackQueue
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.presentation.lyrics.LyricsView
import com.korenskoy.yandexmusic.utils.toCoverUrl
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Time source of the car clock; screenshot tests pin it. */
val LocalCarClock = androidx.compose.runtime.staticCompositionLocalOf<() -> Long> { System::currentTimeMillis }

/** The source tiles under the cover, in order. */
enum class CarSource { WAVE, LIKED, PLAYLISTS, DOWNLOADED }

/** Which source tile is lit for the playing [source]. Pure; unit-tested. */
fun carSourceOf(source: PlaylistSource, isRadio: Boolean): CarSource? = when {
    isRadio -> null
    source == PlaylistSource.VIBE -> CarSource.WAVE
    source == PlaylistSource.LIBRARY -> CarSource.LIKED
    source == PlaylistSource.PLAYLIST -> CarSource.PLAYLISTS
    source == PlaylistSource.OFFLINE -> CarSource.DOWNLOADED
    else -> null
}

/** "1:22" / "−3:24": elapsed and remaining time of the readout. Pure; unit-tested. */
fun carTimeReadout(positionMs: Long, durationMs: Long): Pair<String, String> {
    fun fmt(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000L)
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }
    val position = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    return fmt(position) to "−" + fmt((durationMs - position).coerceAtLeast(0L))
}

/** "1 трек", "3 трека", "25 треков". Pure; unit-tested. */
fun tracksLabel(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    val word = when {
        mod100 in 11..14 -> "треков"
        mod10 == 1 -> "трек"
        mod10 in 2..4 -> "трека"
        else -> "треков"
    }
    return "$count $word"
}

/** Flat dark palette of the car screen: solid surfaces, no gradients, glass or shadows. */
private object Flat {
    val Ground = Color(0xFF0E1013)
    val Surface = Color(0xFF1A1D22)
    val SurfaceHigh = Color(0xFF262A31)
    val TextDim = Color(0xFF9AA1AB)
    val Accent = Color(0xFF3D8BFF)
    val AccentSoft = Color(0x383D8BFF)
}

/**
 * "В авто": a flat, low-cost layout for a head unit, also in a split window next to the navigator.
 * The driver's side holds what plays now: the cover with the title and the like/dislike pair, a seek
 * bar and one row of controls (shuffle · previous · play · next · repeat). The far side always holds
 * the lyrics or the queue (a tab switch; the playlists picker takes its place on demand) over the
 * source tiles. Laid out in design units scaled to the window, mirrored for right-hand drive.
 *
 * [progress] is read where it is drawn (bar, lyrics) or once a second (times), so the screen is not
 * recomposed on every progress tick.
 */
@Composable
fun CarScreen(
    currentTrack: TrackDetailsDto?,
    isPlaying: Boolean,
    likedTrackIds: Set<String>,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onToggleLike: (TrackDetailsDto) -> Unit,
    onDislikeClick: (TrackDetailsDto) -> Unit,
    onPlayMyVibe: () -> Unit,
    onPlayFavorites: () -> Unit,
    onPlayHits: () -> Unit,
    onPlayDownloaded: () -> Unit,
    onExit: () -> Unit,
    isLandscape: Boolean = false,
    steeringWheelOnLeft: Boolean = true,
    progress: () -> Long = { 0L },
    durationMs: Long = 0L,
    onSeek: (Long) -> Unit = {},
    nextTrack: TrackDetailsDto? = null,
    source: PlaylistSource = PlaylistSource.NONE,
    isLiked: Boolean = currentTrack?.let { likedTrackIds.contains(it.idString) } == true,
    isBuffering: Boolean = false,
    playlists: List<UserPlaylistDto> = emptyList(),
    startingPlaylistKind: String? = null,
    onPlayPlaylist: (UserPlaylistDto) -> Unit = {},
    onOpenPlaylists: () -> Unit = {},
    shuffleEnabled: Boolean = false,
    onToggleShuffle: () -> Unit = {},
    lyrics: Lyrics? = null,
    showLyrics: Boolean = true,
    onToggleLyrics: () -> Unit = {},
    repeatOne: Boolean = false,
    onToggleRepeat: () -> Unit = {},
    queue: List<TrackDetailsDto> = emptyList(),
    onQueueTrackClick: (String) -> Unit = {}
) {
    androidx.activity.compose.BackHandler(onBack = onExit)
    var showPlaylists by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = showPlaylists) { showPlaylists = false }
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(Unit) {
        if (!isPlaying && currentTrack == null) onPlayMyVibe()
    }

    val playPause = { if (!isPlaying && currentTrack == null) onPlayMyVibe() else onPlayPauseClick() }
    val isRadio = currentTrack?.isRadio == true
    val playing = carSourceOf(source, isRadio)
    val active = if (showPlaylists) CarSource.PLAYLISTS else playing
    val sourceLabel = when (playing) {
        CarSource.WAVE -> stringResource(R.string.car_source_wave)
        CarSource.LIKED -> stringResource(R.string.library_liked)
        CarSource.PLAYLISTS -> stringResource(R.string.car_source_playlists)
        CarSource.DOWNLOADED -> stringResource(R.string.car_source_downloaded)
        null -> ""
    }
    val showShuffle = currentTrack != null && !isRadio && PlaybackQueue.shuffleApplies(source)
    // The text tab when the track has lyrics and the user keeps it (the remembered choice), else the queue
    val lyricsTab = lyrics != null && showLyrics
    val pickPlaylist: (UserPlaylistDto) -> Unit = { onPlayPlaylist(it); showPlaylists = false }
    val sources = listOf(
        SourceItem(CarSource.WAVE, Icons.Rounded.Waves, stringResource(R.string.car_source_wave), onPlayMyVibe),
        SourceItem(CarSource.LIKED, Icons.Rounded.Favorite, stringResource(R.string.car_source_liked), onPlayFavorites),
        SourceItem(CarSource.PLAYLISTS, Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.car_source_playlists)) {
            showPlaylists = !showPlaylists
            if (showPlaylists) onOpenPlaylists()
        },
        SourceItem(CarSource.DOWNLOADED, Icons.Rounded.DownloadDone, stringResource(R.string.car_source_downloaded), onPlayDownloaded)
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Flat.Ground)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 50) onPrevClick() else if (dragAmount < -50) onNextClick()
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { playPause() }) }
    ) {
        // Landscape needs 1060 units across (driver side + a 380 side panel) and 720 down: a narrower
        // split window keeps the height and the side panel gives up width
        val s = if (isLandscape) minOf(maxWidth / 1060.dp, maxHeight / 720.dp) else minOf(maxWidth / 600.dp, maxHeight / 1060.dp)
        val u = Scale(s.coerceIn(0.5f, 2f))

        val side: @Composable (Modifier) -> Unit = { modifier ->
            if (showPlaylists) {
                PlaylistsPanel(
                    u, playlists, startingPlaylistKind, pickPlaylist, likedTrackIds.size,
                    { onPlayFavorites(); showPlaylists = false }, { showPlaylists = false }, modifier
                )
            } else {
                SidePanel(
                    u = u, lyrics = lyrics, lyricsTab = lyricsTab, progress = progress,
                    onSelectLyrics = { wantLyrics -> if (wantLyrics != showLyrics) onToggleLyrics() },
                    queue = queue, onQueueTrackClick = onQueueTrackClick,
                    modifier = modifier
                )
            }
        }
        val driver: @Composable (Modifier) -> Unit = { modifier ->
            DriverPanel(
                u = u, track = currentTrack, isPlaying = isPlaying, isBuffering = isBuffering, isLiked = isLiked,
                isRadio = isRadio, progress = progress, durationMs = durationMs, onSeek = onSeek,
                onPlayPause = playPause, onPrev = onPrevClick, onNext = onNextClick,
                onLike = { currentTrack?.let(onToggleLike) }, onDislike = { currentTrack?.let(onDislikeClick) },
                showShuffle = showShuffle, shuffleEnabled = shuffleEnabled, onToggleShuffle = onToggleShuffle,
                repeatOne = repeatOne, onToggleRepeat = onToggleRepeat,
                modifier = modifier
            )
        }

        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.LocalTextStyle.current.copy(lineHeight = TextUnit.Unspecified)
        ) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                CarHeader(u, sourceLabel, currentTrack, isRadio, onExit)
                if (isLandscape) {
                    Row(
                        Modifier.fillMaxSize().padding(start = u(40f), end = u(40f), bottom = u(28f)),
                        horizontalArrangement = Arrangement.spacedBy(u(36f))
                    ) {
                        val driverSide: @Composable () -> Unit = { driver(Modifier.width(u(580f)).fillMaxHeight()) }
                        val farSide: @Composable () -> Unit = {
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                side(Modifier.weight(1f).fillMaxWidth())
                                Spacer(Modifier.height(u(16f)))
                                SourceRow(u, sources, active)
                            }
                        }
                        if (steeringWheelOnLeft) { driverSide(); farSide() } else { farSide(); driverSide() }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(horizontal = u(28f)).padding(bottom = u(24f))) {
                        driver(Modifier.fillMaxWidth().height(u(560f)))
                        Spacer(Modifier.height(u(20f)))
                        side(Modifier.fillMaxWidth().weight(1f))
                        Spacer(Modifier.height(u(16f)))
                        SourceRow(u, sources, active)
                    }
                }
            }
        }
    }
}

/** dp sizes of the design, scaled to the screen. */
private class Scale(val factor: Float) {
    operator fun invoke(v: Float): Dp = (v * factor).dp
    fun sp(v: Float): TextUnit = (v * factor).sp
}

private data class SourceItem(val source: CarSource, val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** Top line: back, what plays (source · title · artist), the clock. */
@Composable
private fun CarHeader(u: Scale, sourceLabel: String, track: TrackDetailsDto?, isRadio: Boolean, onExit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = u(40f), end = u(40f), top = u(18f), bottom = u(14f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlatRound(u(64f), stringResource(R.string.car_exit), onClick = onExit) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(u(30f)))
        }
        Spacer(Modifier.width(u(20f)))
        Column(Modifier.weight(1f)) {
            Text(
                (sourceLabel.ifEmpty { stringResource(R.string.car_label) }).uppercase(),
                color = Flat.TextDim,
                fontSize = u.sp(13f),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.14.em,
                maxLines = 1
            )
            Text(
                track?.title ?: stringResource(R.string.car_source_wave),
                color = Color.White,
                fontSize = u.sp(34f),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.01).em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (isRadio) stringResource(R.string.mini_player_live) else track?.artists?.joinToString { it.name ?: "" }.orEmpty(),
                color = Flat.TextDim,
                fontSize = u.sp(21f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(u(20f)))
        CarClock(u)
    }
}

/** Large bold time over a small-caps date; refreshes once a minute. */
@Composable
private fun CarClock(u: Scale) {
    val clock = LocalCarClock.current
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    LaunchedEffect(clock) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE · d MMMM", Locale.forLanguageTag("ru"))
        while (true) {
            val now = clock()
            val calendar = Calendar.getInstance().apply { timeInMillis = now }
            time = timeFormat.format(calendar.time)
            date = dateFormat.format(calendar.time).uppercase(Locale.forLanguageTag("ru"))
            delay((60_000L - now % 60_000L).coerceIn(1_000L, 60_000L))
        }
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(time, color = Color.White, fontSize = u.sp(38f), fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em, style = Tabular)
        Text(date, color = Flat.TextDim, fontSize = u.sp(12f), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.14.em, maxLines = 1)
    }
}

/** Tabular digits: the clock and the times don't jump as numbers change. */
private val Tabular = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")

@Composable
private fun DriverPanel(
    u: Scale,
    track: TrackDetailsDto?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isLiked: Boolean,
    isRadio: Boolean,
    progress: () -> Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    showShuffle: Boolean,
    shuffleEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    repeatOne: Boolean,
    onToggleRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier) {
        // The cover in the middle, flanked by the rating pair: dislike on the left, like on the right
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlatRound(u(92f), stringResource(R.string.cd_ne_nravitsya), onClick = onDislike) {
                Icon(Icons.Rounded.HeartBroken, contentDescription = null, tint = Color.White, modifier = Modifier.size(u(40f)))
            }
            // Weighted, so the buttons are measured first and keep their full round size
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = u(20f))
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(u(24f)))
                    .background(Flat.Surface)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(track?.coverUri.toCoverUrl("600x600")).size(600).build(),
                    contentDescription = track?.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            FlatRound(u(92f), stringResource(R.string.cd_nravitsya), onClick = onLike) {
                Icon(
                    if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isLiked) Color(0xFFFF4D6D) else Color.White,
                    modifier = Modifier.size(u(42f))
                )
            }
        }

        Spacer(Modifier.height(u(22f)))
        SeekBar(u, progress, durationMs, enabled = !isRadio && durationMs > 0, onSeek = onSeek)
        TimeRow(u, progress, durationMs, isRadio)
        Spacer(Modifier.height(u(18f)))

        // One row of controls: shuffle · previous · play · next · repeat
        Row(
            Modifier.fillMaxWidth().height(u(100f)),
            horizontalArrangement = Arrangement.spacedBy(u(12f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeButton(
                u, Icons.Rounded.Shuffle, active = shuffleEnabled, enabled = showShuffle,
                description = stringResource(if (shuffleEnabled) R.string.car_shuffle_on else R.string.car_shuffle_off),
                onClick = onToggleShuffle
            )
            Pill(u, stringResource(R.string.cd_predyduschiy_trek), Modifier.weight(1f), onClick = onPrev) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(u(48f)))
            }
            Pill(u, stringResource(R.string.cd_vosproizvedenie_ili_pauza), Modifier.weight(1.35f), primary = true, onClick = onPlayPause) {
                if (isBuffering) {
                    CircularProgressIndicator(color = Flat.Ground, strokeWidth = u(4f), modifier = Modifier.size(u(40f)))
                } else {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = Flat.Ground, modifier = Modifier.size(u(56f)))
                }
            }
            Pill(u, stringResource(R.string.cd_sleduyuschiy_trek), Modifier.weight(1f), onClick = onNext) {
                Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(u(48f)))
            }
            ModeButton(
                u, Icons.Rounded.RepeatOne, active = repeatOne, enabled = !isRadio,
                description = stringResource(if (repeatOne) R.string.car_repeat_on else R.string.car_repeat_off),
                onClick = onToggleRepeat
            )
        }
    }
}

/**
 * The track progress as a flat bar; a tap or a drag seeks. The finger area is taller than the bar.
 * Progress is read while drawing only.
 */
@Composable
private fun SeekBar(u: Scale, progress: () -> Long, durationMs: Long, enabled: Boolean, onSeek: (Long) -> Unit) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val seekTo = { fraction: Float -> onSeek((fraction.coerceIn(0f, 1f) * durationMs).toLong()) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(u(44f))
            .then(
                if (!enabled) Modifier
                else Modifier
                    .pointerInput(durationMs) { detectTapGestures { seekTo(it.x / size.width) } }
                    .pointerInput(durationMs) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragFraction = (it.x / size.width).coerceIn(0f, 1f) },
                            onDragEnd = { dragFraction?.let(seekTo); dragFraction = null },
                            onDragCancel = { dragFraction = null }
                        ) { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    }
            )
            .drawBehind {
                val barHeight = u(10f).toPx()
                val top = (size.height - barHeight) / 2f
                val radius = CornerRadius(barHeight / 2f)
                drawRoundRect(Flat.SurfaceHigh, Offset(0f, top), Size(size.width, barHeight), radius)
                val fraction = dragFraction ?: if (durationMs > 0) (progress().toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                if (fraction > 0f) drawRoundRect(Color.White, Offset(0f, top), Size(size.width * fraction, barHeight), radius)
                if (enabled) drawCircle(Color.White, radius = u(11f).toPx(), center = Offset(size.width * fraction, size.height / 2f))
            }
    )
}

/** Elapsed on the left, remaining on the right; recomposes once a second. */
@Composable
private fun TimeRow(u: Scale, progress: () -> Long, durationMs: Long, isRadio: Boolean) {
    val seconds by remember(durationMs) { derivedStateOf { progress() / 1000L } }
    Row(Modifier.fillMaxWidth()) {
        if (isRadio) {
            Text(stringResource(R.string.radio_on_air), color = Flat.TextDim, fontSize = u.sp(18f), fontWeight = FontWeight.Bold)
        } else {
            val (elapsed, remaining) = carTimeReadout(seconds * 1000L, durationMs)
            Text(elapsed, color = Color.White, fontSize = u.sp(20f), fontWeight = FontWeight.SemiBold, style = Tabular)
            Spacer(Modifier.weight(1f))
            Text(remaining, color = Flat.TextDim, fontSize = u.sp(20f), fontWeight = FontWeight.Medium, style = Tabular)
        }
    }
}

@Composable
private fun Pill(
    u: Scale,
    description: String,
    modifier: Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(u(50f)))
            .background(if (primary) Color.White else Flat.Surface)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun FlatRound(size: Dp, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Flat.Surface)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Shuffle / repeat at the ends of the control row: round, blue while on, dimmed when it does not apply. */
@Composable
private fun ModeButton(u: Scale, icon: ImageVector, active: Boolean, enabled: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(u(84f))
            .clip(CircleShape)
            .background(if (active && enabled) Flat.AccentSoft else Flat.Surface)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when {
                !enabled -> Flat.TextDim.copy(alpha = 0.35f)
                active -> Flat.Accent
                else -> Color.White
            },
            modifier = Modifier.size(u(38f))
        )
    }
}

/** Big icon tiles of the sources; the playing one is lit. */
@Composable
private fun SourceRow(u: Scale, items: List<SourceItem>, active: CarSource?) {
    Row(Modifier.fillMaxWidth().height(u(84f)), horizontalArrangement = Arrangement.spacedBy(u(12f))) {
        items.forEach { item -> SourceTile(u, item, item.source == active) }
    }
}

@Composable
private fun RowScope.SourceTile(u: Scale, item: SourceItem, selected: Boolean) {
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(u(26f)))
            .background(if (selected) Flat.AccentSoft else Flat.Surface)
            .clickable(role = Role.Button, onClickLabel = item.label, onClick = item.onClick)
            .semantics { contentDescription = item.label },
        contentAlignment = Alignment.Center
    ) {
        Icon(item.icon, contentDescription = null, tint = if (selected) Flat.Accent else Color.White, modifier = Modifier.size(u(42f)))
    }
}

/** A flat card on the far side with a title and a close button (the playlists picker). */
@Composable
private fun PanelCard(u: Scale, title: String, onClose: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(u(28f)))
            .background(Flat.Surface)
            .padding(u(20f))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = u.sp(26f), fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(u(60f))
                    .clip(CircleShape)
                    .background(Flat.SurfaceHigh)
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.car_playlists_close), tint = Color.White, modifier = Modifier.size(u(30f)))
            }
        }
        Spacer(Modifier.height(u(14f)))
        content()
    }
}

/**
 * The far side: lyrics or the queue, switched with two tabs on top. Without lyrics only the queue is
 * there (the text tab is greyed out).
 */
@Composable
private fun SidePanel(
    u: Scale,
    lyrics: Lyrics?,
    lyricsTab: Boolean,
    progress: () -> Long,
    onSelectLyrics: (Boolean) -> Unit,
    queue: List<TrackDetailsDto>,
    onQueueTrackClick: (String) -> Unit,
    modifier: Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(u(28f)))
            .background(Flat.Surface)
            .padding(u(14f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(u(64f))
                .clip(RoundedCornerShape(u(32f)))
                .background(Flat.Ground)
                .padding(u(5f)),
            horizontalArrangement = Arrangement.spacedBy(u(6f))
        ) {
            TabPill(u, Icons.Rounded.Subtitles, stringResource(R.string.car_tab_lyrics), selected = lyricsTab, enabled = lyrics != null) { onSelectLyrics(true) }
            TabPill(u, Icons.Rounded.LibraryMusic, stringResource(R.string.car_tab_queue), selected = !lyricsTab, enabled = true) { onSelectLyrics(false) }
        }
        Spacer(Modifier.height(u(10f)))
        if (lyricsTab && lyrics != null) {
            LyricsView(
                lyrics = lyrics,
                progress = progress,
                textSize = u.sp(30f),
                lineSpacing = u(8f),
                modifier = Modifier.fillMaxSize().padding(horizontal = u(14f))
            )
        } else {
            QueueList(u, queue, onQueueTrackClick)
        }
    }
}

@Composable
private fun RowScope.TabPill(u: Scale, icon: ImageVector, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tint = when {
        selected -> Color.White
        enabled -> Flat.TextDim
        else -> Flat.TextDim.copy(alpha = 0.35f)
    }
    Row(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(u(28f)))
            .background(if (selected) Flat.SurfaceHigh else Color.Transparent)
            .clickable(enabled = enabled && !selected, role = Role.Tab, onClickLabel = label, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(u(28f)))
        Spacer(Modifier.width(u(10f)))
        Text(label, color = tint, fontSize = u.sp(19f), fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** What plays next; a tap jumps there. */
@Composable
private fun QueueList(u: Scale, queue: List<TrackDetailsDto>, onPick: (String) -> Unit) {
    val context = LocalContext.current
    if (queue.isEmpty()) {
        Text(stringResource(R.string.car_queue_empty), color = Flat.TextDim, fontSize = u.sp(18f), modifier = Modifier.padding(u(14f)))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        // No keys: a queue may hold the same track twice
        items(queue) { track ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(u(18f)))
                    .clickable(role = Role.Button, onClick = { onPick(track.idString) })
                    .padding(vertical = u(8f), horizontal = u(8f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(track.coverUri.toCoverUrl("200x200")).size(200).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(u(64f)).clip(RoundedCornerShape(u(14f))).background(Flat.SurfaceHigh)
                )
                Spacer(Modifier.width(u(16f)))
                Column(Modifier.weight(1f)) {
                    Text(track.title.orEmpty(), color = Color.White, fontSize = u.sp(21f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artists.joinToString { it.name ?: "" }, color = Flat.TextDim, fontSize = u.sp(16f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * "Мне нравится" and the user's playlists (own and liked) as big cover tiles. A tap starts the playlist
 * (shuffle mode applies); the tile shows a spinner while its tracks load.
 */
@Composable
private fun PlaylistsPanel(
    u: Scale,
    playlists: List<UserPlaylistDto>,
    startingKind: String?,
    onPick: (UserPlaylistDto) -> Unit,
    likedCount: Int,
    onPickLiked: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    PanelCard(u, stringResource(R.string.car_playlists_title), onClose, modifier) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(u(150f)),
            horizontalArrangement = Arrangement.spacedBy(u(16f)),
            verticalArrangement = Arrangement.spacedBy(u(18f)),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "liked") {
                PlaylistTile(u, stringResource(R.string.car_liked_tile), tracksLabel(likedCount), onPickLiked) {
                    Box(Modifier.fillMaxSize().background(Color(0xFFE5486A)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(u(64f)))
                    }
                }
            }
            items(playlists, key = { it.key }) { playlist ->
                PlaylistTile(u, playlist.title.orEmpty(), tracksLabel(playlist.trackCount), { onPick(playlist) }) {
                    val cover = playlist.coverUri.toCoverUrl("300x300")
                    if (cover != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(cover).size(300).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = Flat.TextDim, modifier = Modifier.size(u(56f)))
                        }
                    }
                    if (startingKind == playlist.key) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = u(4f), modifier = Modifier.size(u(48f)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistTile(u: Scale, title: String, subtitle: String, onClick: () -> Unit, cover: @Composable () -> Unit) {
    Column(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(u(20f)))
                .background(Flat.SurfaceHigh)
        ) { cover() }
        Text(title, color = Color.White, fontSize = u.sp(18f), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = u(8f)))
        Text(subtitle, color = Flat.TextDim, fontSize = u.sp(14f), maxLines = 1)
    }
}
