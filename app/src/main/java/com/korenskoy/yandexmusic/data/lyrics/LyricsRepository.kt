package com.korenskoy.yandexmusic.data.lyrics

import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lyrics of a track: synced (LRC) when there are any, else plain text, else none. The answer is kept in
 * memory for the last tracks, so going back and forth does not refetch; a network failure is not kept.
 */
@Singleton
class LyricsRepository @Inject constructor(
    private val api: YandexMusicApi
) {
    // The lyrics file lives on a storage host: a plain client, so the OAuth token is not sent there
    private val files = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cache = object : LinkedHashMap<String, Lyrics?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics?>?) = size > 64
    }

    /** @throws Exception on a network error (the caller just shows no lyrics then). */
    suspend fun lyrics(trackId: String): Lyrics? {
        val id = LyricsSign.trackIdOf(trackId)
        synchronized(cache) { if (cache.containsKey(id)) return cache[id] }
        val synced = fetch(id, "LRC")?.let { LyricsParser.parseLrc(it) }?.takeIf { lines -> lines.any { it.text.isNotBlank() } }
        val result: Lyrics? = if (synced != null) {
            Lyrics.Synced(synced)
        } else {
            fetch(id, "TEXT")?.trim()?.takeIf { it.isNotEmpty() }?.let { Lyrics.Plain(it) }
        }
        synchronized(cache) { cache[id] = result }
        return result
    }

    /** The lyrics file in [format], or null when the track has none in it. */
    private suspend fun fetch(id: String, format: String): String? = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis() / 1000
        val info = try {
            api.getLyrics(id, format, timestamp, LyricsSign.sign(id, timestamp)).result
        } catch (e: retrofit2.HttpException) {
            // No lyrics (or none in this format) comes back as an HTTP error
            if (e.code() in 400..499) return@withContext null else throw e
        }
        val url = info?.downloadUrl ?: return@withContext null
        files.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body.string() else null
        }
    }
}
