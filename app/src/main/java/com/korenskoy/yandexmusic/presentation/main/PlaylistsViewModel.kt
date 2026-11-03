package com.korenskoy.yandexmusic.presentation.main

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.korenskoy.yandexmusic.R
import com.korenskoy.yandexmusic.data.dto.TrackDetailsDto
import com.korenskoy.yandexmusic.data.dto.UserPlaylistDto
import com.korenskoy.yandexmusic.data.player.PlaybackController
import com.korenskoy.yandexmusic.data.player.PlaylistSource
import com.korenskoy.yandexmusic.data.repository.UserPlaylistsRepository
import com.korenskoy.yandexmusic.data.repository.UserPlaylistsRepository.AddTrackResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The user's playlists: the opened one, and edits (create, rename, delete, add and remove tracks).
 * The repository updates the list and the cache, so the library, the opened playlist and Android Auto
 * follow by themselves; edits only report their outcome to the user.
 */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: UserPlaylistsRepository,
    private val playbackController: PlaybackController,
    private val downloadScheduler: com.korenskoy.yandexmusic.data.download.DownloadScheduler
) : ViewModel() {

    /** The user's own playlists (the ones tracks can be added to). */
    val playlists: StateFlow<List<UserPlaylistDto>> = repository.playlists

    /** Own playlists, then the liked ones of other people: for browsing and playing. */
    val allPlaylists: StateFlow<List<UserPlaylistDto>> =
        combine(repository.playlists, repository.likedPlaylists) { own, liked -> own + liked }
            .stateIn(viewModelScope, SharingStarted.Eagerly, repository.playlists.value + repository.likedPlaylists.value)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    // region Opened playlist

    private val _opened = MutableStateFlow<OpenedPlaylist?>(null)
    val opened: StateFlow<OpenedPlaylist?> = _opened.asStateFlow()
    private var openJob: Job? = null

    init {
        // Edits (here or in Android Auto's list), server refreshes and account changes
        allPlaylists.onEach { _ ->
            val opened = _opened.value ?: return@onEach
            val now = repository.find(opened.playlist.key)
            when {
                now == null -> close() // deleted, or another account
                now.revision != opened.playlist.revision -> open(now, keepShownTracks = true)
                now != opened.playlist -> _opened.value = opened.copy(playlist = now)
            }
        }.launchIn(viewModelScope)
    }

    /** @param keepShownTracks reload in place (after an edit) instead of showing the spinner. */
    fun open(playlist: UserPlaylistDto, keepShownTracks: Boolean = false) {
        openJob?.cancel()
        val shown = _opened.value?.takeIf { keepShownTracks && it.playlist.key == playlist.key }?.tracks.orEmpty()
        _opened.value = OpenedPlaylist(playlist, shown, isLoading = shown.isEmpty())
        openJob = viewModelScope.launch {
            val loaded = try {
                OpenedPlaylist(playlist, repository.tracks(playlist), isLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Playlist load failed", e)
                OpenedPlaylist(playlist, isLoading = false, failed = true)
            }
            if (_opened.value?.playlist?.key == playlist.key) _opened.value = loaded
        }
    }

    fun close() {
        openJob?.cancel()
        _opened.value = null
    }

    fun play(shuffle: Boolean) {
        val tracks = _opened.value?.tracks.orEmpty().let { if (shuffle) it.shuffled() else it }
        tracks.firstOrNull()?.let { playbackController.playTrack(it.idString, tracks, PlaylistSource.PLAYLIST) }
    }

    fun playTrack(trackId: String) {
        val tracks = _opened.value?.tracks ?: return
        playbackController.playTrack(trackId, tracks, PlaylistSource.PLAYLIST)
    }

    /** The playlist being loaded to start from the car screen (its kind), for a spinner on its tile. */
    private val _starting = MutableStateFlow<String?>(null)
    val starting: StateFlow<String?> = _starting.asStateFlow()
    private var startJob: Job? = null

    /** Downloads the playlist for offline listening (in the background, survives closing the app). */
    fun download(playlist: UserPlaylistDto) {
        viewModelScope.launch {
            downloadScheduler.downloadPlaylist(playlist.key)
            say(R.string.playlist_msg_download_started, playlist.title.orEmpty())
        }
    }

    /** Fresh list from the server (a playlist made on the phone shows up in the car). */
    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    /** Plays a whole playlist without opening it (car screen); shuffle mode applies as usual. */
    fun playPlaylist(playlist: UserPlaylistDto) {
        startJob?.cancel()
        _starting.value = playlist.key
        startJob = viewModelScope.launch {
            try {
                val tracks = repository.tracks(playlist)
                val first = if (playbackController.shuffleEnabled.value) tracks.randomOrNull() else tracks.firstOrNull()
                if (first == null) {
                    _messages.tryEmit(context.getString(R.string.car_playlist_empty, playlist.title.orEmpty()))
                } else {
                    playbackController.playTrack(first.idString, tracks, PlaylistSource.PLAYLIST)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Playlist start failed", e)
                _messages.tryEmit(context.getString(R.string.playlist_msg_failed))
            } finally {
                if (_starting.value == playlist.key) _starting.value = null
            }
        }
    }

    // endregion

    // region Editing

    /** Creates a playlist; with [thenAdd] the track goes straight into it. */
    fun create(title: String, thenAdd: TrackDetailsDto? = null) = edit {
        val created = repository.create(title)
        if (thenAdd != null) {
            repository.addTrack(created, thenAdd)
            say(R.string.playlist_msg_added, created.title ?: title)
        } else {
            say(R.string.playlist_msg_created, created.title ?: title)
        }
    }

    fun rename(playlist: UserPlaylistDto, title: String) = edit {
        repository.rename(playlist, title)
        say(R.string.playlist_msg_renamed, title.trim())
    }

    fun delete(playlist: UserPlaylistDto) = edit {
        repository.delete(playlist)
        say(R.string.playlist_msg_deleted, playlist.title.orEmpty())
    }

    fun addTrack(playlist: UserPlaylistDto, track: TrackDetailsDto) = edit {
        val result = repository.addTrack(playlist, track)
        say(if (result == AddTrackResult.ALREADY_THERE) R.string.playlist_msg_already else R.string.playlist_msg_added, playlist.title.orEmpty())
    }

    fun removeTrack(playlist: UserPlaylistDto, trackId: String) = edit {
        repository.removeTrack(playlist, trackId)
        say(R.string.playlist_msg_removed, playlist.title.orEmpty())
    }

    /** Saves a new track order; when the playlist changed elsewhere meanwhile, reloads it instead. */
    fun reorder(playlist: UserPlaylistDto, shownOrder: List<String>, newOrder: List<String>) {
        if (shownOrder == newOrder) return
        viewModelScope.launch {
            try {
                repository.reorder(playlist, shownOrder, newOrder)
                say(R.string.playlist_msg_reordered, playlist.title.orEmpty())
            } catch (e: CancellationException) {
                throw e
            } catch (e: UserPlaylistsRepository.PlaylistChangedException) {
                _messages.tryEmit(context.getString(R.string.playlist_msg_changed_elsewhere))
                repository.refresh()
            } catch (e: Exception) {
                Log.e(TAG, "Reorder failed", e)
                _messages.tryEmit(context.getString(R.string.playlist_msg_failed))
            }
        }
    }

    fun setPublic(playlist: UserPlaylistDto, public: Boolean) = edit {
        repository.setPublic(playlist, public)
        say(if (public) R.string.playlist_msg_public else R.string.playlist_msg_private, playlist.title.orEmpty())
    }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Playlist edit failed", e)
                _messages.tryEmit(context.getString(R.string.playlist_msg_failed))
            }
        }
    }

    private fun say(resId: Int, title: String) {
        _messages.tryEmit(context.getString(resId, title))
    }

    // endregion

    private companion object {
        const val TAG = "Playlists"
    }
}

/** A playlist opened from the library, with its tracks once loaded. */
data class OpenedPlaylist(
    val playlist: UserPlaylistDto,
    val tracks: List<TrackDetailsDto> = emptyList(),
    val isLoading: Boolean = true,
    val failed: Boolean = false
)
