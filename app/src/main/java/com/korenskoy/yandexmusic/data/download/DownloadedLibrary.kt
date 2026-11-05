package com.korenskoy.yandexmusic.data.download

import android.content.Context
import android.util.Log
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.repository.LibraryRepository
import com.korenskoy.yandexmusic.data.repository.UserPlaylistsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything that is on the device, with its track info: downloaded liked tracks, tracks of downloaded
 * playlists and the offline wave. One list for "Скачанные" in the library, the car screen's tile and
 * the offline mix, so they always agree.
 *
 * Track info comes from the liked list, the cached playlist track lists, the offline wave's index and
 * an own index of tracks downloaded by hand ([remember]), so a track stays listed even if it later
 * leaves the playlist it was downloaded from.
 */
@Singleton
class DownloadedLibrary @Inject constructor(
    @ApplicationContext context: Context,
    private val downloadManager: TrackDownloadManager,
    private val libraryRepository: LibraryRepository,
    private val playlistsRepository: UserPlaylistsRepository,
    private val waveCache: WaveCache,
    private val json: Json
) {
    private val file = File(context.filesDir, "downloaded_tracks.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var saved = LinkedHashMap<String, TrackDetailsDto>()

    private val _tracks = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    /** Liked first, then playlist tracks, then the offline wave; only what is on disk. */
    val tracks: StateFlow<List<TrackDetailsDto>> = _tracks.asStateFlow()

    init {
        scope.launch {
            mutex.withLock { load() }
            combine(
                downloadManager.downloadedTrackIds,
                libraryRepository.likedTracks,
                waveCache.tracks,
                playlistsRepository.playlists,
                playlistsRepository.likedPlaylists
            ) { _, _, _, _, _ -> Unit }
                .collectLatest {
                    delay(300) // downloads finish in bursts: rebuild once
                    rebuild()
                }
        }
    }

    /** These tracks were just downloaded by hand: keep their info. */
    suspend fun remember(tracks: List<TrackDetailsDto>) {
        if (tracks.isEmpty()) return
        mutex.withLock {
            tracks.forEach { saved[it.idString] = it }
            runCatching { file.writeText(json.encodeToString(saved.values.toList())) }
                .onFailure { Log.w(TAG, "Downloaded index not saved", it) }
        }
        rebuild()
    }

    private suspend fun rebuild() {
        val onDisk = downloadManager.downloadedTrackIds.value
        val fromPlaylists = (playlistsRepository.playlists.value + playlistsRepository.likedPlaylists.value)
            .flatMap { playlistsRepository.cachedTracks(it) }
        val known = mutex.withLock { saved.values.toList() }
        _tracks.value = (libraryRepository.likedTracks.value + fromPlaylists + known + waveCache.tracks.value)
            .distinctBy { it.idString }
            .filter { it.idString in onDisk }
    }

    private fun load() {
        saved = runCatching { json.decodeFromString<List<TrackDetailsDto>>(file.readText()) }.getOrNull().orEmpty()
            .associateByTo(LinkedHashMap()) { it.idString }
    }

    private companion object {
        const val TAG = "DownloadedLibrary"
    }
}
