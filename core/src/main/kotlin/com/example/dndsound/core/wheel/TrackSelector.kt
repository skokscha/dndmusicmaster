package com.example.dndsound.core.wheel

import com.example.dndsound.core.model.Mood
import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.Track
import com.example.dndsound.core.model.WheelPoint
import kotlin.random.Random

/**
 * Picks the next music track for a wheel point.
 *
 * Strict sector binding: a track only ever plays inside its own sector.
 * The pool for a wheel point is
 *  - battle mode: every battle track (the battle folder is its own pool);
 *  - the calm zone: calm-center tracks only;
 *  - a mood sector: tracks whose position falls into that same sector.
 * An empty sector pool means silence — nothing leaks in from other folders.
 *
 * Within the pool the most recently played tracks are excluded
 * (min(5, n - 1), falling back to the full pool) and the pick is random with
 * weight 1/(distance + 0.05). [next] keeps the pool of the currently playing
 * track, so auto-advance at track end never jumps folders.
 */
class TrackSelector(private val random: Random = Random.Default) {

    fun select(
        tracks: List<Track>,
        point: WheelPoint,
        mode: MusicMode,
        recentIds: List<String> = emptyList(),
    ): Track? = pick(poolFor(tracks, point, mode), point, recentIds)

    /** Auto-advance: next track from the currently playing track's own sector. */
    fun next(tracks: List<Track>, current: Track, recentIds: List<String> = emptyList()): Track? =
        pick(poolFor(tracks, current.position, current.mode), current.position, recentIds)

    private fun pick(pool: List<Track>, point: WheelPoint, recentIds: List<String>): Track? {
        if (pool.isEmpty()) return null
        val recentCount = minOf(MAX_RECENT, pool.size - 1)
        val recent = recentIds.take(recentCount).toSet()
        var candidates = pool.filter { it.id !in recent }
        if (candidates.isEmpty()) candidates = pool

        val weights = candidates.map { 1.0 / (it.position.distanceTo(point) + 0.05) }
        var roll = random.nextDouble(weights.sum())
        for ((index, track) in candidates.withIndex()) {
            roll -= weights[index]
            if (roll <= 0.0) return track
        }
        return candidates.last()
    }

    private fun poolFor(tracks: List<Track>, point: WheelPoint, mode: MusicMode): List<Track> {
        val byMode = tracks.filter { it.mode == mode }
        if (mode == MusicMode.BATTLE) return byMode
        return if (point.isCalm()) {
            byMode.filter { it.position.isCalm() }
        } else {
            val mood = WheelMath.nearestMood(point.angleDeg)
            byMode.filter { sectorOf(it.position) == mood }
        }
    }

    /** Mood sector of a track position; calm-center tracks belong to no sector. */
    private fun sectorOf(position: WheelPoint): Mood? =
        if (position.isCalm()) null else WheelMath.nearestMood(position.angleDeg)

    private companion object {
        const val MAX_RECENT = 5
    }
}
