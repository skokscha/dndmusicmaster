package com.example.dndsound.core.wheel

import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.Track
import com.example.dndsound.core.model.WheelPoint
import kotlin.random.Random

/**
 * Picks the next music track for a wheel point.
 *
 * Rules: filter by mode, exclude the most recently played tracks
 * (min(5, n - 1)), prefer candidates within [R_MAX] of the point (fall back
 * to the 3 nearest), then choose randomly with weight 1/(distance + 0.05).
 * Empty library (or no matching mode) returns null.
 */
class TrackSelector(private val random: Random = Random.Default) {

    fun select(
        tracks: List<Track>,
        point: WheelPoint,
        mode: MusicMode,
        recentIds: List<String> = emptyList(),
    ): Track? {
        val pool = tracks.filter { it.mode == mode }
        if (pool.isEmpty()) return null

        val recentCount = minOf(MAX_RECENT, pool.size - 1)
        val recent = recentIds.take(recentCount).toSet()
        var candidates = pool.filter { it.id !in recent }
        if (candidates.isEmpty()) candidates = pool

        val sorted = candidates.sortedBy { it.position.distanceTo(point) }
        val inRadius = sorted.filter { it.position.distanceTo(point) <= R_MAX }
        val chosen = if (inRadius.isNotEmpty()) inRadius else sorted.take(NEAREST_FALLBACK)

        val weights = chosen.map { 1.0 / (it.position.distanceTo(point) + 0.05) }
        var roll = random.nextDouble(weights.sum())
        for ((index, track) in chosen.withIndex()) {
            roll -= weights[index]
            if (roll <= 0.0) return track
        }
        return chosen.last()
    }

    private companion object {
        const val MAX_RECENT = 5
        const val R_MAX = 0.45f
        const val NEAREST_FALLBACK = 3
    }
}
