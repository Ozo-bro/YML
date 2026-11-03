package com.korenskoy.yandexmusic.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsTest {

    @Test
    fun parsesLrcWithMetadataAndSeveralTags() {
        val lrc = """
            [ar:Кино]
            [ti:Группа крови]
            [00:12.50]Тёплое место, но улицы ждут
            [00:17.3]Отпечатков наших ног
            [01:02.123][02:10.00]Группа крови на рукаве
            [02:30.00]
        """.trimIndent()
        val lines = LyricsParser.parseLrc(lrc)
        assertEquals(listOf(12_500L, 17_300L, 62_123L, 130_000L, 150_000L), lines.map { it.timeMs })
        assertEquals("Тёплое место, но улицы ждут", lines[0].text)
        assertEquals("Группа крови на рукаве", lines[3].text)
        assertEquals("", lines[4].text)
    }

    @Test
    fun currentLineFollowsThePosition() {
        val lines = listOf(LyricLine(1_000, "a"), LyricLine(5_000, "b"), LyricLine(9_000, "c"))
        assertEquals(-1, LyricsParser.currentIndex(lines, 500))
        assertEquals(0, LyricsParser.currentIndex(lines, 1_000))
        assertEquals(1, LyricsParser.currentIndex(lines, 8_999))
        assertEquals(2, LyricsParser.currentIndex(lines, 60_000))
        assertEquals(-1, LyricsParser.currentIndex(emptyList(), 1_000))
    }

    @Test
    fun base64MatchesTheStandard() {
        assertEquals("", LyricsSign.base64(ByteArray(0)))
        assertEquals("Zg==", LyricsSign.base64("f".toByteArray()))
        assertEquals("Zm8=", LyricsSign.base64("fo".toByteArray()))
        assertEquals("Zm9vYmFy", LyricsSign.base64("foobar".toByteArray()))
        assertEquals("/+8=", LyricsSign.base64(byteArrayOf(0xFF.toByte(), 0xEF.toByte())))
    }

    @Test
    fun signsTheTrackIdWithoutTheAlbum() {
        assertEquals("12345", LyricsSign.trackIdOf("12345:678"))
        assertEquals(44, LyricsSign.sign("12345", 1_700_000_000).length) // 32-byte HMAC in Base64
    }
}
