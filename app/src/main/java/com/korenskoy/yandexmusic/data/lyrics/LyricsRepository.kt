package com.korenskoy.yandexmusic.data.lyrics

import android.content.Context
import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lyrics of a track: synced (LRC) when there are any, else plain text, else none. Found lyrics are also
 * saved on the device, so they show without internet (downloaded tracks get theirs when they are
 * saved, see [prefetch]); the last answers are kept in memory too. A network failure is not kept.
 */
@Singleton
class LyricsRepository internal constructor(
    private val api: YandexMusicApi,
    private val dir: File,
    private val files: OkHttpClient
) {
    @Inject constructor(api: YandexMusicApi, @ApplicationContext context: Context) : this(
        api,
        File(context.filesDir, "lyrics"),
        // Lyrics files are hosted separately. Use a client without the OAuth interceptor.
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    )

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
        var failure: Exception? = null
        suspend fun attempt(load: suspend () -> String?): String? {
            return try {
                load()?.trim()?.takeIf { it.isNotEmpty() }
            } catch (e: HttpException) {
                if (e.code() != 404 && e.code() != 410) failure = failure ?: e
                null
            } catch (e: IOException) {
                failure = failure ?: e
                null
            } catch (e: SerializationException) {
                failure = failure ?: e
                null
            }
        }

        val lrc = attempt { fetch(id, "LRC") }
        val lines = lrc?.let(LyricsParser::parseLrc)
        val result = if (lrc != null && lines != null && lines.any { it.text.isNotBlank() }) {
            save(id, SYNCED, lrc)
            Lyrics.Synced(lines)
        } else {
            val text = attempt { fetch(id, "TEXT") } ?: attempt {
                api.getTrackSupplement(id).result?.lyrics
                    ?.takeIf { it.hasRights != false }
                    ?.fullLyrics
            }
            text?.let {
                save(id, PLAIN, it)
                Lyrics.Plain(it)
            }
        }
        // A failed request can succeed the next time the track is played.
        if (result == null) failure?.let { throw it }
        synchronized(cache) { cache[id] = result }
        return result
    }

    /** Saves the lyrics of a track that was just downloaded, so it has them offline. Never throws. */
    suspend fun prefetch(trackId: String) {
        val id = LyricsSign.trackIdOf(trackId)
        if (fileOf(id).exists()) return
        try {
            lyrics(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No lyrics this time: the track still plays; they are fetched when it is played online
        }
    }

    private fun fileOf(id: String) = File(dir, "$id.txt")

    /** The first line says the format (SYNCED / PLAIN), the rest is the lyrics as Yandex sent them. */
    private suspend fun readSaved(id: String): Lyrics? = withContext(Dispatchers.IO) {
        val file = fileOf(id)
        if (!file.exists()) return@withContext null
        val text = runCatching { file.readText() }.getOrNull() ?: return@withContext null
        val body = text.substringAfter('\n', "")
        when (text.substringBefore('\n')) {
            SYNCED -> LyricsParser.parseLrc(body).takeIf { lines -> lines.any { it.text.isNotBlank() } }
                ?.let { Lyrics.Synced(it) }
            PLAIN -> body.takeIf { it.isNotBlank() }?.let { Lyrics.Plain(it) }
            else -> null
        }
    }

    private suspend fun save(id: String, kind: String, body: String) = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
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
        val url = api.getLyrics(id, format, timestamp, LyricsSign.sign(id, timestamp))
            .result?.downloadUrl?.takeIf { it.isNotBlank() } ?: return@withContext null
        val downloadUrl = url.toHttpUrlOrNull() ?: throw IOException("Invalid lyrics download URL")
        files.newCall(Request.Builder().url(downloadUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Lyrics download returned HTTP ${response.code}")
            response.body.string()
        }
    }
}
