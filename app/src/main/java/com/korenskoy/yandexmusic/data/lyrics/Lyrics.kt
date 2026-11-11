package com.korenskoy.yandexmusic.data.lyrics

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Song lyrics: time-synced lines (LRC) or, when the track has no synced version, plain text. */
sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics
    data class Plain(val text: String) : Lyrics
}

data class LyricLine(val timeMs: Long, val text: String)

/** LRC parsing and the current line. Pure; unit-tested in LyricsTest. */
object LyricsParser {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /** "[01:23.45]text" lines, several tags per line allowed; metadata tags ([ar:...]) are skipped. */
    fun parseLrc(lrc: String): List<LyricLine> {
        val result = ArrayList<LyricLine>()
        for (raw in lrc.removePrefix("\uFEFF").lineSequence()) {
            var rest = raw.trim()
            val times = ArrayList<Long>()
            while (true) {
                val match = timeTag.matchAt(rest, 0) ?: break
                val (min, sec, frac) = match.destructured
                val fracMs = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                times += min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs
                rest = rest.substring(match.range.last + 1)
            }
            if (times.isEmpty()) continue
            val text = rest.trim()
            times.forEach { result += LyricLine(it, text) }
        }
        return result.sortedBy { it.timeMs }
    }

    /** Index of the line sung at [positionMs], or -1 before the first one. */
    fun currentIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var low = 0
        var high = lines.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }
}

/** The request signature of the lyrics endpoint: base64(HMAC-SHA256("{trackId}{timestamp}")). */
object LyricsSign {
    // The key the Yandex Music Android app signs lyrics requests with (public, as in the yandex-music-api library)
    private const val KEY = "p93jhgh689SBReK6ghtw62"

    fun sign(trackId: String, timestampSec: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(KEY.toByteArray(), "HmacSHA256"))
        return base64(mac.doFinal("$trackId$timestampSec".toByteArray()))
    }

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** Standard Base64 with padding (java.util.Base64 needs API 26, android.util.Base64 is absent in unit tests). */
    fun base64(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
            out.append(ALPHABET[b0 shr 2])
            out.append(ALPHABET[((b0 and 0x03) shl 4) or (b1 shr 4)])
            out.append(if (i + 1 < bytes.size) ALPHABET[((b1 and 0x0F) shl 2) or (b2 shr 6)] else '=')
            out.append(if (i + 2 < bytes.size) ALPHABET[b2 and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }

    /** "12345:678" (track:album) → "12345". */
    fun trackIdOf(id: String): String = id.substringBefore(':')
}
