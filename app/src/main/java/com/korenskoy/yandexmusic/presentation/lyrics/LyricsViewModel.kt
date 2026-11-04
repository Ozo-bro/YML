package com.korenskoy.yandexmusic.presentation.lyrics

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.korenskoy.yandexmusic.data.lyrics.Lyrics
import com.korenskoy.yandexmusic.data.lyrics.LyricsRepository
import com.korenskoy.yandexmusic.data.player.PlaybackController
import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Lyrics of the playing track, loaded as soon as it starts. */
data class LyricsState(val trackId: String? = null, val lyrics: Lyrics? = null)

@HiltViewModel
class LyricsViewModel @Inject constructor(
    playbackController: PlaybackController,
    private val repository: LyricsRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LyricsState())
    val state: StateFlow<LyricsState> = _state.asStateFlow()

    /** Lyrics replace the cover whenever there are any (on by default). */
    val showLyrics: StateFlow<Boolean> = settingsRepository.showLyrics
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        viewModelScope.launch {
            playbackController.currentTrack
                .map { track -> track?.idString }
                .distinctUntilChanged()
                .collectLatest { id ->
                    _state.value = LyricsState(id, null)
                    if (id == null) return@collectLatest
                    val lyrics = try {
                        repository.lyrics(id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Lyrics load failed", e)
                        null
                    }
                    _state.value = LyricsState(id, lyrics)
                }
        }
    }

    fun toggleShowLyrics() {
        viewModelScope.launch { settingsRepository.setShowLyrics(!showLyrics.value) }
    }

    private companion object {
        const val TAG = "Lyrics"
    }
}
