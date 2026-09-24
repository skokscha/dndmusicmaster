package com.example.dndsound.core.audio

import com.example.dndsound.core.mixer.Crossfade
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shared equal-power fade machinery for the engines. Ownership is per handle:
 * a newer fade supersedes the older one only on the handles it actually
 * touches, so a base-loop fade-in and a layer fade-in (different handles) can
 * run at the same time while two fades fighting over the same player resolve
 * in favor of the newer one. The mutex additionally guarantees the superseded
 * fade has exited before the new one writes its first volume.
 *
 * IMPORTANT: reserve the generation with [begin] synchronously when the fade
 * is initiated, listing every handle the fade will write, then pass the id to
 * the suspend calls — even if the actual fade runs in a child coroutine that
 * starts later. Reserving inside the child would let a pending fade supersede
 * a newer fade issued in between.
 *
 * The outgoing player continues from its current volume, which keeps an
 * interrupted fade click-free; the incoming one starts from 0.
 */
class FadeCoordinator(private val rampStepMs: Long = 25) {

    private var fadeCounter = 0L
    private val owner = mutableMapOf<PlayerHandle, Long>()
    private val mutex = Mutex()

    /** Reserves the fade slot for these handles; call synchronously. */
    fun begin(handles: Collection<PlayerHandle>): Long {
        val id = ++fadeCounter
        handles.forEach { owner[it] = id }
        return id
    }

    fun begin(handle: PlayerHandle): Long = begin(listOf(handle))

    private fun owns(id: Long, handle: PlayerHandle) = owner[handle] == id

    /** Equal-power crossfade; returns false when superseded before finishing. */
    suspend fun crossfade(
        generation: Long,
        outgoing: PlayerHandle?,
        incoming: PlayerHandle,
        incomingTargetLinear: Float,
        durationMs: Long,
    ): Boolean {
        mutex.withLock {
            val steps = ((durationMs + rampStepMs - 1) / rampStepMs).toInt().coerceAtLeast(1)
            val outStart = outgoing?.volume ?: 0f
            for (i in 0..steps) {
                if (!owns(generation, incoming)) return false
                if (outgoing != null && !owns(generation, outgoing)) return false
                val t = i.toFloat() / steps
                outgoing?.setVolume(outStart * Crossfade.fadeOut(t))
                incoming.setVolume(incomingTargetLinear * Crossfade.fadeIn(t))
                delay(rampStepMs)
            }
            return owns(generation, incoming) && (outgoing == null || owns(generation, outgoing))
        }
    }

    /** Smooth ramp of one handle to a target, continuing from its current volume. */
    suspend fun rampTo(generation: Long, handle: PlayerHandle, targetLinear: Float, durationMs: Long): Boolean =
        rampAllTo(generation, mapOf(handle to targetLinear), durationMs)

    /**
     * Ramps several handles to their own targets in one fade, each continuing
     * from its current volume (used e.g. to duck/unduck every active player).
     */
    suspend fun rampAllTo(
        generation: Long,
        targets: Map<PlayerHandle, Float>,
        durationMs: Long,
    ): Boolean {
        if (targets.isEmpty()) return true
        mutex.withLock {
            val steps = ((durationMs + rampStepMs - 1) / rampStepMs).toInt().coerceAtLeast(1)
            val starts = targets.mapValues { it.key.volume }
            for (i in 0..steps) {
                if (targets.keys.any { !owns(generation, it) }) return false
                val t = i.toFloat() / steps
                targets.forEach { (handle, target) ->
                    val start = starts.getValue(handle)
                    handle.setVolume(start + (target - start) * t)
                }
                delay(rampStepMs)
            }
            return targets.keys.all { owns(generation, it) }
        }
    }

    /** Fades every given handle down to silence from its current volume. */
    suspend fun fadeOutAll(generation: Long, handles: List<PlayerHandle>, durationMs: Long): Boolean =
        rampAllTo(generation, handles.associateWith { 0f }, durationMs)
}
