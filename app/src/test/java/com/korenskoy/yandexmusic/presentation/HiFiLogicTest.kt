package com.korenskoy.yandexmusic.presentation

import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.presentation.car.CarSource
import com.korenskoy.yandexmusic.presentation.car.carSourceOf
import com.korenskoy.yandexmusic.presentation.car.carTimeReadout
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import com.korenskoy.yandexmusic.presentation.hifi.TintMath
import com.korenskoy.yandexmusic.presentation.main.components.formatWaveCacheInputGb
import com.korenskoy.yandexmusic.presentation.main.components.formatWaveCacheLimitMb
import com.korenskoy.yandexmusic.presentation.main.components.parseWaveCacheLimitGbToMb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Pure logic of the design: cover tint and the car screen's readouts. */
class HiFiLogicTest {

    // --- Cover tint ------------------------------------------------------------------------------

    private fun hsv(argb: Int): FloatArray =
        TintMath.rgbToHsv(((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f)

    @Test
    fun hsvRoundTripKeepsTheColor() {
        listOf(0xFF0A6CFF.toInt(), 0xFFFF3366.toInt(), 0xFF33CC66.toInt(), 0xFF808080.toInt(), 0xFFFFCC00.toInt()).forEach { c ->
            val h = hsv(c)
            val back = TintMath.hsvToArgb(h[0], h[1], h[2])
            for (shift in intArrayOf(16, 8, 0)) {
                val d = abs(((c shr shift) and 0xFF) - ((back shr shift) and 0xFF))
                assertTrue("channel drift $d for ${Integer.toHexString(c)}", d <= 1)
            }
            assertEquals("opaque", 0xFF, (back ushr 24))
        }
    }

    @Test
    fun tintIsNeverMuddyNorWashedOut() {
        listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF101010.toInt(), 0xFF203040.toInt(), 0xFFFFE0E0.toInt(), 0xFF0A6CFF.toInt())
            .forEach { c ->
                val v = hsv(TintMath.normalize(c))
                assertTrue("value ${v[2]} in 0.42..0.80 for ${Integer.toHexString(c)}", v[2] in 0.41f..0.81f)
            }
    }

    @Test
    fun colorfulCoversKeepTheirHueAndGetEnoughColor() {
        val pale = hsv(TintMath.normalize(0xFFD9A0A0.toInt())) // pale red
        assertTrue("hue stays red: ${pale[0]}", pale[0] < 10f || pale[0] > 350f)
        assertTrue("saturation lifted: ${pale[1]}", pale[1] >= 0.34f)
    }

    @Test
    fun greyCoversBecomeQuietBlueGrey() {
        val grey = hsv(TintMath.normalize(0xFF777777.toInt()))
        assertTrue("hue ~220: ${grey[0]}", abs(grey[0] - 220f) < 6f)
        assertTrue("barely saturated: ${grey[1]}", grey[1] in 0.10f..0.18f)
    }

    // --- Car -------------------------------------------------------------------------------------

    @Test
    fun dockLightsThePlayingSource() {
        assertEquals(CarSource.WAVE, carSourceOf(PlaylistSource.VIBE))
        assertEquals(CarSource.LIKED, carSourceOf(PlaylistSource.LIBRARY))
        assertEquals(CarSource.PLAYLISTS, carSourceOf(PlaylistSource.PLAYLIST))
        assertEquals(CarSource.DOWNLOADED, carSourceOf(PlaylistSource.OFFLINE))
        assertEquals("hits are not in the dock", null, carSourceOf(PlaylistSource.TRENDS))
        assertNull(carSourceOf(PlaylistSource.SEARCH))
        assertNull(carSourceOf(PlaylistSource.NONE))
    }

    @Test
    fun timeReadoutShowsElapsedAndRemaining() {
        assertEquals("1:22" to "−3:24", carTimeReadout(82_000, 286_000))
        assertEquals("0:00" to "−4:46", carTimeReadout(0, 286_000))
        assertEquals("0:05" to "−0:00", carTimeReadout(5_400, 5_400))
        assertEquals("clamped past the end", "4:46" to "−0:00", carTimeReadout(400_000, 286_000))
        assertEquals("negative position", "0:00" to "−1:00", carTimeReadout(-50, 60_000))
        assertEquals("unknown duration", "0:00" to "−0:00", carTimeReadout(10_000, 0))
        assertEquals("long tracks", "61:01" to "−0:00", carTimeReadout(3_661_000, 3_661_000))
    }

    @Test
    fun waveCachePresetsReach8Gb() {
        assertEquals(listOf(0, 1024, 2048, 4096, 8192), SettingsRepository.WAVE_CACHE_LIMITS_MB)
        assertEquals(128 * 1024, SettingsRepository.MAX_WAVE_CACHE_LIMIT_MB)
    }

    @Test
    fun waveCacheCustomGbInputParsesAndFormats() {
        assertEquals(8192, parseWaveCacheLimitGbToMb("8"))
        assertEquals(12288, parseWaveCacheLimitGbToMb(" 12 "))
        assertEquals(1536, parseWaveCacheLimitGbToMb("1.5"))
        assertEquals(1536, parseWaveCacheLimitGbToMb("1,5"))
        assertEquals(512, parseWaveCacheLimitGbToMb("0,5"))
        assertEquals(0, parseWaveCacheLimitGbToMb("0"))
        assertEquals(SettingsRepository.MAX_WAVE_CACHE_LIMIT_MB, parseWaveCacheLimitGbToMb("128"))

        assertNull(parseWaveCacheLimitGbToMb(""))
        assertNull(parseWaveCacheLimitGbToMb("   "))
        assertNull(parseWaveCacheLimitGbToMb("-1"))
        assertNull(parseWaveCacheLimitGbToMb("129"))
        assertNull(parseWaveCacheLimitGbToMb("abc"))
        assertNull(parseWaveCacheLimitGbToMb("NaN"))

        assertEquals("Выкл", formatWaveCacheLimitMb(0, "Выкл"))
        assertEquals("512 МБ", formatWaveCacheLimitMb(512, "Выкл"))
        assertEquals("8 ГБ", formatWaveCacheLimitMb(8192, "Выкл"))
        assertEquals("1,5 ГБ", formatWaveCacheLimitMb(1536, "Выкл"))
        assertEquals("1,25 ГБ", formatWaveCacheLimitMb(1280, "Выкл"))

        assertEquals("", formatWaveCacheInputGb(0))
        assertEquals("8", formatWaveCacheInputGb(8192))
        assertEquals("1,5", formatWaveCacheInputGb(1536))
        assertEquals("0,001", formatWaveCacheInputGb(1))
    }
}
