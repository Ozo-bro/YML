package com.korenskoy.yandexmusic.data.player

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.SystemClock
import android.util.Log

/**
 * The audio session shared by both players (crossfade), announced to equalizer apps the standard way
 * (AudioEffect "open/close control session" broadcasts). Poweramp Equalizer, Wavelet and the like then
 * start in the background and attach to the music by themselves: no auto-start app, no screen popping up.
 */
object AudioEffectSession {
    private const val TAG = "AudioEffectSession"
    /** Poweramp Equalizer: also told directly, as Android 8+ does not deliver the broadcast to a sleeping app otherwise. */
    const val POWERAMP_EQUALIZER = "com.maxmpz.equalizer"
    private const val REANNOUNCE_MS = 30_000L

    @Volatile
    var sessionId: Int = AudioManager.ERROR
        private set
    @Volatile
    private var lastOpenAt = 0L

    /** One session id for the app's lifetime; [AudioManager.ERROR] when the system could not give one. */
    fun obtain(context: Context): Int {
        if (sessionId <= 0) {
            sessionId = runCatching {
                (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
            }.getOrDefault(AudioManager.ERROR)
        }
        return sessionId
    }

    /** Music starts: equalizers attach. Repeated at most every 30 s, so an equalizer restarted meanwhile finds us again. */
    fun announceOpen(context: Context) {
        if (sessionId <= 0) return
        val now = SystemClock.elapsedRealtime()
        if (lastOpenAt != 0L && now - lastOpenAt < REANNOUNCE_MS) return
        lastOpenAt = now
        send(context, AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
    }

    /** The player goes away: equalizers let go. */
    fun announceClose(context: Context) {
        if (sessionId <= 0) return
        lastOpenAt = 0L
        send(context, AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
    }

    private fun send(context: Context, action: String) {
        val intent = Intent(action)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        runCatching { context.sendBroadcast(intent) } // equalizers that are running
            .onFailure { Log.w(TAG, "Broadcast failed", it) }
        runCatching { context.sendBroadcast(Intent(intent).setPackage(POWERAMP_EQUALIZER)) } // and a sleeping Poweramp EQ
    }
}
