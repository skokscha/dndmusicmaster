package com.example.dndsound.core.wheel

import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.Track
import com.example.dndsound.core.model.WheelPoint
import kotlin.random.Random

/** A selector outcome: the track plus the zone it will actually play from. */
data class Selection(
    val track: Track,
    val playingZone: WheelZone,
    /** True when the target zone was empty and a neighbouring zone donated the track. */
    val isFallback: Boolean,
)

/**
 * Picks the next music track for a wheel zone (docs "Выбор трека"):
 *
 * 1. candidates are tracks of the requested mode whose derived zone equals
 *    the target, minus the last min(5, n - 1) played (recent are allowed back
 *    if that empties the pool);
 * 2. within the pool the pick is random with weight 1/(distance + 0.1);
 * 3. an empty target zone falls back to the zone nearest to the pointer that
 *    still has tracks — [Selection.isFallback] marks it so the UI can show a
 *    "playing: <zone>" chip;
 * 4. no mode tracks at all -> null (silence).
 *
 * [next] keeps the pool of the currently playing track's own zone, so
 * auto-advance at track end never jumps folders.
 */
class TrackSelector(private val random: Random = Random.Default) {

    fun select(
        target: WheelZone,
        point: WheelPoint,
        mode: MusicMode,
        tracks: List<Track>,
        recentIds: List<String> = emptyList(),
        cfg: WheelConfig = WheelConfig(),
    ): Selection? {
        val pool = tracks.filter { it.mode == mode && it.position != null }
        val inZone = pool.filter { WheelZones.zoneAt(it.position!!, cfg) == target }
        val candidates = withoutRecent(inZone, recentIds)
        if (candidates.isNotEmpty()) return Selection(weightedPick(candidates, point), target, isFallback = false)

        // Fallback: the nearest zone that still has tracks of this mode.
        if (pool.isEmpty()) return null
        val nearest = pool.groupBy { WheelZones.zoneAt(it.position!!, cfg) }
            .minBy { (zone, _) -> WheelZones.distanceToZone(point, zone, cfg) }
        val picks = withoutRecent(nearest.value, recentIds).ifEmpty { nearest.value }
        return Selection(weightedPick(picks, point), nearest.key, isFallback = true)
    }

    /** Auto-advance: next track from the currently playing track's own zone. */
    fun next(
        tracks: List<Track>,
        current: Track,
        recentIds: List<String> = emptyList(),
        cfg: WheelConfig = WheelConfig(),
    ): Selection? {
        val position = current.position ?: return null
        return select(WheelZones.zoneAt(position, cfg), position, current.mode, tracks, recentIds, cfg)
    }

    private fun withoutRecent(pool: List<Track>, recentIds: List<String>): List<Track> {
        if (pool.isEmpty()) return pool
        val recentCount = minOf(MAX_RECENT, pool.size - 1)
        val recent = recentIds.take(recentCount).toSet()
        return pool.filter { it.id !in recent }
    }

    private fun weightedPick(candidates: List<Track>, point: WheelPoint): Track {
        val weights = candidates.map { 1.0 / (it.position!!.distanceTo(point) + 0.1) }
        var roll = random.nextDouble(weights.sum())
        for ((index, track) in candidates.withIndex()) {
            roll -= weights[index]
            if (roll <= 0.0) return track
        }
        return candidates.last()
    }

    private companion object {
        const val MAX_RECENT = 5
    }
}
