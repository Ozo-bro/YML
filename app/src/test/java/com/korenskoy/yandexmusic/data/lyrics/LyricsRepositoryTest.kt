package com.korenskoy.yandexmusic.data.lyrics

import com.korenskoy.yandexmusic.data.network.YandexMusicApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException

class LyricsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun syncedLyricsTakePriorityAndAreSavedForOfflinePlayback() = runBlocking {
        val repository = repository { url ->
            when {
                url.encodedPath == "/tracks/123/lyrics" && url.queryParameter("format") == "LRC" ->
                    downloadAt("/synced")
                url.encodedPath == "/synced" -> 200 to "[00:01.25]Synced line"
                else -> error("Unexpected request: $url")
            }
        }

        val expected = Lyrics.Synced(listOf(LyricLine(1_250, "Synced line")))
        assertEquals(expected, repository.lyrics("123:456"))
        assertEquals(expected, repository { error("Unexpected network request: $it") }.lyrics("123"))
    }

    @Test
    fun failedSyncedDownloadStillAllowsPlainLyrics() = runBlocking {
        val repository = repository { url ->
            when (url.encodedPath) {
                "/tracks/123/lyrics" -> downloadAt("/${url.queryParameter("format")}")
                "/LRC" -> throw IOException("Download timed out")
                "/TEXT" -> 200 to "Plain line"
                else -> error("Unexpected request: $url")
            }
        }

        assertEquals(Lyrics.Plain("Plain line"), repository.lyrics("123"))
    }

    @Test
    fun fallsBackToFullLyricsFromTrackSupplement() = runBlocking {
        val repository = repository { url ->
            when (url.encodedPath) {
                "/tracks/123/lyrics" -> 404 to "{}"
                "/tracks/123/supplement" -> 200 to
                    """{"result":{"lyrics":{"lyrics":"Preview","fullLyrics":"Full text","hasRights":true}}}"""
                else -> error("Unexpected request: $url")
            }
        }

        assertEquals(Lyrics.Plain("Full text"), repository.lyrics("123"))
    }

    @Test
    fun supplementWithoutDisplayRightsIsIgnored() = runBlocking {
        val repository = repository { url ->
            when (url.encodedPath) {
                "/tracks/123/lyrics" -> 404 to "{}"
                "/tracks/123/supplement" -> 200 to
                    """{"result":{"lyrics":{"fullLyrics":"Full text","hasRights":false}}}"""
                else -> error("Unexpected request: $url")
            }
        }

        assertNull(repository.lyrics("123"))
    }

    @Test
    fun failedDownloadCanBeRetriedOnTheNextLookup() = runBlocking {
        var storageAvailable = false
        val repository = repository { url ->
            when {
                url.encodedPath == "/tracks/123/lyrics" && url.queryParameter("format") == "LRC" ->
                    downloadAt("/synced")
                url.encodedPath == "/synced" && storageAvailable -> 200 to "[00:02]Recovered line"
                url.encodedPath == "/synced" -> 403 to ""
                else -> 404 to "{}"
            }
        }

        assertThrows(IOException::class.java) { runBlocking { repository.lyrics("123") } }
        storageAvailable = true
        assertEquals(Lyrics.Synced(listOf(LyricLine(2_000, "Recovered line"))), repository.lyrics("123"))
    }

    private fun repository(reply: (HttpUrl) -> Pair<Int, String>): LyricsRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val (code, body) = reply(request.url)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Test response")
                .body(body.toResponseBody())
                .build()
        }.build()
        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit.Builder()
            .baseUrl("https://api.example.test/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(YandexMusicApi::class.java)
        return LyricsRepository(api, folder.root, client)
    }

    private fun downloadAt(path: String) =
        200 to """{"result":{"downloadUrl":"https://storage.example.test$path"}}"""
}
