package com.korenskoy.yandexmusic.data.player

/** Where the current queue came from. */
enum class PlaylistSource {
    LIBRARY, VIBE, SEARCH, TRENDS, RADIO, NONE,
    /** One of the user's own playlists. New values go last: the name is persisted in the session file. */
    PLAYLIST,
    /** No connection: the downloaded tracks (liked + offline wave); back to My Wave once online. */
    OFFLINE
}

/** Sleep timer: either a moment in [android.os.SystemClock.elapsedRealtime] time or "at the end of this track". */
data class SleepTimerState(val endsAtElapsedMs: Long?, val atEndOfTrack: Boolean)

/** What "next" should do for a queue position. Pure logic, unit-tested in PlaybackQueueTest. */
sealed interface NextAction {
    data class PlayIndex(val index: Int) : NextAction
    /** End of My Wave: fetch more tracks and continue. */
    data object FetchMoreWave : NextAction
    /** Nothing to continue from: fall back to the liked tracks. */
    data object FallbackLibrary : NextAction
}

object PlaybackQueue {
    /** Apple Music behaviour: "previous" after this much playback restarts the current track. */
    const val RESTART_ON_PREV_THRESHOLD_MS = 3_000L
    /** Queue window persisted around the current track to keep the session file small. */
    const val SNAPSHOT_BEFORE = 50
    const val SNAPSHOT_AFTER = 150

    fun nextAction(queueSize: Int, currentIndex: Int, source: PlaylistSource): NextAction = when {
        currentIndex != -1 && currentIndex + 1 < queueSize -> NextAction.PlayIndex(currentIndex + 1)
        source == PlaylistSource.VIBE -> NextAction.FetchMoreWave
        // Regular playlists loop back to the start so playback never silently stops.
        queueSize > 0 -> NextAction.PlayIndex(0)
        else -> NextAction.FallbackLibrary
    }

    /** Index of the previous track, or null when "previous" should restart the current one. */
    fun previousIndex(currentIndex: Int, progressMs: Long): Int? =
        if (progressMs > RESTART_ON_PREV_THRESHOLD_MS || currentIndex <= 0) null else currentIndex - 1

    /**
     * Index of the first item after [currentIndex] (wrapping around, never [currentIndex] itself) that
     * satisfies [accept], or null. Used offline to jump to the next downloaded track.
     */
    fun <T> nextMatching(queue: List<T>, currentIndex: Int, accept: (T) -> Boolean): Int? {
        if (queue.isEmpty()) return null
        for (step in 1..queue.size) {
            val index = Math.floorMod(currentIndex + step, queue.size)
            if (index == currentIndex) return null
            if (accept(queue[index])) return index
        }
        return null
    }

    /** Shuffle mode applies to everything except My Wave (an endless stream) and radio. */
    fun shuffleApplies(source: PlaylistSource): Boolean =
        source != PlaylistSource.VIBE && source != PlaylistSource.RADIO

    /** Shuffle: the track at [currentIndex] goes first, the rest follow in random order. */
    fun <T> shuffledAround(queue: List<T>, currentIndex: Int, random: kotlin.random.Random = kotlin.random.Random): List<T> {
        if (currentIndex !in queue.indices) return queue.shuffled(random)
        val rest = queue.filterIndexed { index, _ -> index != currentIndex }.shuffled(random)
        return listOf(queue[currentIndex]) + rest
    }

    /**
     * Shuffle off: back to the [original] order, keeping only what is still in [shuffled] (tracks removed
     * meanwhile stay removed) and appending what was added to the shuffled queue.
     */
    fun <T, K> restoreOrder(original: List<T>, shuffled: List<T>, key: (T) -> K): List<T> {
        val present = shuffled.mapTo(HashSet()) { key(it) }
        val kept = original.filter { key(it) in present }
        val keptKeys = kept.mapTo(HashSet()) { key(it) }
        return kept + shuffled.filter { key(it) !in keptKeys }
    }

    /** Same tracks in the same order: picking a track inside the playing queue, not starting a new one. */
    fun <T, K> isSameQueue(a: List<T>, b: List<T>, key: (T) -> K): Boolean =
        a === b || (a.size == b.size && a.indices.all { key(a[it]) == key(b[it]) })

    fun <T> snapshotWindow(queue: List<T>, currentIndex: Int): List<T> {
        if (currentIndex < 0) return queue.take(SNAPSHOT_AFTER)
        val from = (currentIndex - SNAPSHOT_BEFORE).coerceAtLeast(0)
        val to = (currentIndex + SNAPSHOT_AFTER).coerceAtMost(queue.size)
        return queue.subList(from, to)
    }
}
