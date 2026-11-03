package com.korenskoy.yandexmusic.presentation

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.github.takahirom.roborazzi.captureRoboImage
import com.korenskoy.yandexmusic.data.dto.ArtistDto
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.presentation.car.CarScreen
import com.korenskoy.yandexmusic.presentation.car.LocalCarClock
import com.korenskoy.yandexmusic.presentation.hifi.ArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.HiFiBackdrop
import com.korenskoy.yandexmusic.presentation.hifi.LocalArtworkTint
import com.korenskoy.yandexmusic.presentation.hifi.LocalHiFiLite
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The car screen in a 1920×720 head unit's split window (music ≈ 2/3 of the width), at two common
 * head-unit densities. Rendered for review only (CI uploads them), not compared against references.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class CarSplitScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(context).coroutineContext(Dispatchers.Unconfined).build())
    }

    private fun track(id: String, title: String, artist: String) =
        TrackDetailsDto(id = JsonPrimitive(id), title = title, artists = listOf(ArtistDto(name = artist)), durationMs = 215_000)

    private val current = track("1", "Группа крови", "Кино")
    private val next = track("2", "Звезда по имени Солнце", "Кино")
    private val playlists = listOf(
        UserPlaylistDto(kind = JsonPrimitive(1003), title = "В дорогу", trackCount = 42),
        UserPlaylistDto(kind = JsonPrimitive(1004), title = "Спокойное", trackCount = 1),
        UserPlaylistDto(kind = JsonPrimitive(1005), title = "Рок 90-х и очень длинное название", trackCount = 23),
        UserPlaylistDto(kind = JsonPrimitive(1006), title = "Утро", trackCount = 12),
        UserPlaylistDto(kind = JsonPrimitive(1007), title = "Вечер", trackCount = 5)
    )
    private val tint = ArtworkTint(Color(0xFF3F6FC4), Color(0xFF1B2A6B))

    private val lyrics = com.korenskoy.yandexmusic.data.lyrics.Lyrics.Synced(
        listOf(
            "Тёплое место, но улицы ждут", "Отпечатков наших ног", "Звёздная пыль на сапогах",
            "Мягкое кресло, клетчатый плед", "Не нажатый вовремя курок", "Солнечный день в ослепительных снах",
            "Группа крови на рукаве", "Мой порядковый номер на рукаве"
        ).mapIndexed { i, text -> com.korenskoy.yandexmusic.data.lyrics.LyricLine(60_000L + i * 6_000L, text) }
    )

    private fun show(shuffle: Boolean = true, withLyrics: Boolean = false) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            YandexMusicTheme {
                CompositionLocalProvider(LocalArtworkTint provides tint, LocalHiFiLite provides true, LocalCarClock provides { 1_792_567_800_000L }) {
                    Box(Modifier.fillMaxSize()) {
                        HiFiBackdrop(tint = tint)
                        CarScreen(
                            currentTrack = current, isPlaying = true, likedTrackIds = emptySet(), onPlayPauseClick = {}, onNextClick = {},
                            onPrevClick = {}, onToggleLike = {}, onDislikeClick = {}, onPlayMyVibe = {}, onPlayFavorites = {}, onPlayHits = {},
                            onPlayDownloaded = {}, onExit = {}, isLandscape = true, progress = { 82_000L }, durationMs = 215_000,
                            nextTrack = next, source = PlaylistSource.PLAYLIST, playlists = playlists, startingPlaylistKind = "1004",
                            shuffleEnabled = shuffle,
                            lyrics = if (withLyrics) lyrics else null,
                            queue = listOf(next, track("3", "Кукушка", "Кино"), track("4", "Перемен", "Кино")),
                            repeatOne = !shuffle
                        )
                    }
                }
            }
        }
        frames()
    }

    private fun frames() = repeat(10) {
        compose.mainClock.advanceTimeBy(100)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun snap(name: String) = compose.onRoot().captureRoboImage("build/outputs/split-screens/$name.png")

    private fun openPlaylists() {
        compose.onNodeWithContentDescription("Плейлисты").performClick()
        frames()
    }

    // 1920×720 head unit in split mode, sizes measured on the user's photos (the split border moves)
    @Test
    @Config(qualifiers = "w1040dp-h610dp-land-mdpi")
    fun splitWide() { show(); snap("split_1040x610_player") }

    @Test
    @Config(qualifiers = "w1040dp-h610dp-land-mdpi")
    fun splitWidePlaylists() { show(); openPlaylists(); snap("split_1040x610_playlists") }

    @Test
    @Config(qualifiers = "w880dp-h610dp-land-mdpi")
    fun splitNarrow() { show(shuffle = false); snap("split_880x610_player") }

    @Test
    @Config(qualifiers = "w880dp-h610dp-land-mdpi")
    fun splitNarrowPlaylists() { show(); openPlaylists(); snap("split_880x610_playlists") }

    @Test
    @Config(qualifiers = "w1040dp-h610dp-land-mdpi")
    fun splitWideLyrics() { show(withLyrics = true); snap("split_1040x610_lyrics") }

    @Test
    @Config(qualifiers = "w1040dp-h610dp-land-mdpi")
    fun splitWideQueue() { show(); compose.onNodeWithContentDescription("Далее в очереди").performClick(); frames(); snap("split_1040x610_queue") }

    // No split: the whole 1920×720 screen
    @Test
    @Config(qualifiers = "w1920dp-h690dp-land-mdpi")
    fun fullWidth() { show(); snap("full_1920x690_player") }
}
