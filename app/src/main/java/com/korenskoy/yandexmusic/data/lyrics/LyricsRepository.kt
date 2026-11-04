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
 * Lyrics of a track: synced (LRC) when there are any, else plain text, else none. Found lyrics are also
 * saved on the device, so they show without internet (downloaded tracks get theirs when they are
 * saved, see [prefetch]); the last answers are kept in memory too. A network failure is not kept.
 */
@Singleton
class LyricsRepository @Inject constructor(
    private val api: YandexMusicApi,
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
) {
    private val dir = java.io.File(context.filesDir, "lyrics").apply { mkdirs() }
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
        readSaved(id)?.let { saved ->
            synchronized(cache) { cache[id] = saved }
            return saved
        }
        val synced = fetch(id, "LRC")?.takeIf { lrc -> LyricsParser.parseLrc(lrc).any { it.text.isNotBlank() } }
        val result: Lyrics? = if (synced != null) {
            save(id, SYNCED, synced)
            Lyrics.Synced(LyricsParser.parseLrc(synced))
        } else {
            fetch(id, "TEXT")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                save(id, PLAIN, it)
                Lyrics.Plain(it)
            }
        }
        synchronized(cache) { cache[id] = result }
        return result
    }

    /** Saves the lyrics of a track that was just downloaded, so it has them offline. Never throws. */
    suspend fun prefetch(trackId: String) {
        val id = LyricsSign.trackIdOf(trackId)
        if (fileOf(id).exists()) return
        try {
            lyrics(id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // No lyrics this time: the track still plays; they are fetched when it is played online
        }
    }

    private fun fileOf(id: String) = java.io.File(dir, "$id.txt")

    /** The first line says the format (SYNCED / PLAIN), the rest is the lyrics as Yandex sent them. */
    private suspend fun readSaved(id: String): Lyrics? = withContext(Dispatchers.IO) {
        val file = fileOf(id)
        if (!file.exists()) return@withContext null
        val text = runCatching { file.readText() }.getOrNull() ?: return@withContext null
        val body = text.substringAfter('\n', "")
        when (text.substringBefore('\n')) {
            SYNCED -> LyricsParser.parseLrc(body).takeIf { it.isNotEmpty() }?.let { Lyrics.Synced(it) }
            PLAIN -> body.takeIf { it.isNotBlank() }?.let { Lyrics.Plain(it) }
            else -> null
        }
    }

    private suspend fun save(id: String, kind: String, body: String) = withContext(Dispatchers.IO) {
        runCatching {
            fileOf(id).writeText("$kind\n$body")
            // A few KB each; keep the newest ones when there are too many
            val files = dir.listFiles().orEmpty()
            if (files.size > MAX_SAVED) {
                files.sortedBy { it.lastModified() }.take(files.size - MAX_SAVED).forEach { it.delete() }
            }
        }
    }

    private companion object {
        const val SYNCED = "SYNCED"
        const val PLAIN = "PLAIN"
        const val MAX_SAVED = 2000
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
