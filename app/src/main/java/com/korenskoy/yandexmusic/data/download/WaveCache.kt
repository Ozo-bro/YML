package com.korenskoy.yandexmusic.data.download

import android.content.Context
import android.util.Log
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.network.TrackDownloadManager
import com.korenskoy.yandexmusic.data.network.TrackPreloadManager
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Offline wave", like the official app: while My Wave plays online, the next tracks are saved in the
 * background within a size limit (oldest dropped first). Without a connection they play as the offline
 * mix together with the downloaded liked tracks.
 *
 * Only tracks this cache saved itself are ever deleted: anything downloaded by hand (liked tracks,
 * playlists) is left alone, and a cached track the user later downloads by hand leaves the cache.
 */
@Singleton
class WaveCache @Inject constructor(
    @ApplicationContext context: Context,
    private val downloadManager: TrackDownloadManager,
    private val preloadManager: TrackPreloadManager,
    private val settingsRepository: SettingsRepository,
    private val json: Json
) {
    @Serializable
    data class Entry(val track: TrackDetailsDto, val addedAt: Long)

    private val file = File(context.filesDir, "wave_cache.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val pending = Channel<TrackDetailsDto>(Channel.UNLIMITED)
    private var entries = mutableListOf<Entry>()

    private val _tracks = MutableStateFlow<List<TrackDetailsDto>>(emptyList())
    /** Cached wave tracks that are on disk, oldest first: for the offline mix. */
    val tracks: StateFlow<List<TrackDetailsDto>> = _tracks.asStateFlow()

    private val _usedBytes = MutableStateFlow(0L)
    val usedBytes: StateFlow<Long> = _usedBytes.asStateFlow()

    init {
        scope.launch {
            mutex.withLock { load() }
            // A lower limit (or "off") frees space right away
            launch {
                settingsRepository.waveCacheLimitMb.distinctUntilChanged().collect { limit ->
                    mutex.withLock { evictTo(limit); save() }
                }
            }
            for (track in pending) {
                try {
                    process(track)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Wave cache download failed", e)
                }
            }
        }
    }

    /** Wave tracks coming up next: saved in the background unless they are on disk already. */
    fun prefetch(tracks: List<TrackDetailsDto>) {
        tracks.filterNot { it.isRadio }.forEach { pending.trySend(it) }
    }

    /** These tracks were downloaded by hand: never delete them as cache. */
    suspend fun promote(trackIds: Collection<String>) {
        if (trackIds.isEmpty()) return
        val ids = trackIds.toHashSet()
        mutex.withLock {
            if (entries.removeAll { it.track.idString in ids }) {
                publish()
                save()
            }
        }
    }

    private suspend fun process(track: TrackDetailsDto) {
        val limit = settingsRepository.waveCacheLimitMb.first()
        if (limit <= 0) return
        val id = track.idString
        if (downloadManager.isTrackDownloaded(id)) return // cached already, or downloaded by hand
        val stream = preloadManager.resolveForDownload(id) ?: return
        if (!downloadManager.downloadTrack(id, stream.url, stream.quality)) return
        mutex.withLock {
            entries.removeAll { it.track.idString == id }
            entries += Entry(track, System.currentTimeMillis())
            evictTo(limit)
            save()
        }
    }

    /** Drops the oldest cached tracks until the cache fits in [limitMb] (0 = off: drops all). */
    private suspend fun evictTo(limitMb: Int) {
        entries.retainAll { downloadManager.isTrackDownloaded(it.track.idString) }
        val limitBytes = limitMb.toLong() * 1024 * 1024
        var total = entries.sumOf { sizeOf(it) }
        while (entries.isNotEmpty() && total > limitBytes) {
            val oldest = entries.removeAt(0)
            total -= sizeOf(oldest)
            downloadManager.removeTrack(oldest.track.idString)
        }
        publish()
    }

    private fun sizeOf(entry: Entry): Long = downloadManager.getLocalTrackFile(entry.track.idString)?.length() ?: 0L

    private fun publish() {
        _tracks.value = entries.map { it.track }
        _usedBytes.value = entries.sumOf { sizeOf(it) }
    }

    private fun load() {
        entries = runCatching { json.decodeFromString<List<Entry>>(file.readText()) }.getOrNull().orEmpty()
            .filter { downloadManager.isTrackDownloaded(it.track.idString) } // the music cache may have been cleared
            .sortedBy { it.addedAt }
            .toMutableList()
        publish()
    }

    private fun save() {
        runCatching { file.writeText(json.encodeToString(entries.toList())) }
            .onFailure { Log.w(TAG, "Wave cache index not saved", it) }
    }

    private companion object {
        const val TAG = "WaveCache"
    }
}
