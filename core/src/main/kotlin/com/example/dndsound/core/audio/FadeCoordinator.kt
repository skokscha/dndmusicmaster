package com.example.dndsound.core.audio

import com.example.dndsound.core.mixer.Crossfade
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shared equal-power fade machinery for the engines. A newer fade supersedes
 * the older one at its next ramp step (generation counter) and starts only
 * after the superseded fade has exited (mutex), so two fades never write
 * volumes to the same handle at once.
 *
 * IMPORTANT: reserve the generation with [begin] synchronously when the fade
 * is initiated, then pass it to the suspend calls — even if the actual fade
 * runs in a child coroutine that starts later. Reserving inside the child
 * would let a pending fade supersede a newer fade issued in between.
 *
 * The outgoing player continues from its current volume, which keeps an
 * interrupted fade click-free; the incoming one starts from 0.
 */
class FadeCoordinator(private val rampStepMs: Long = 25) {

    private var generation = 0L
    private val mutex = Mutex()

    /** Reserves the fade slot; call synchronously, then pass to the fade. */
    fun begin(): Long = ++generation

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
                if (generation != this.generation) return false
                val t = i.toFloat() / steps
                outgoing?.setVolume(outStart * Crossfade.fadeOut(t))
                incoming.setVolume(incomingTargetLinear * Crossfade.fadeIn(t))
                delay(rampStepMs)
            }
            return generation == this.generation
        }
    }

    /** Smooth ramp of one handle to a target, continuing from its current volume. */
    suspend fun rampTo(generation: Long, handle: PlayerHandle, targetLinear: Float, durationMs: Long): Boolean {
        mutex.withLock {
            val steps = ((durationMs + rampStepMs - 1) / rampStepMs).toInt().coerceAtLeast(1)
            val start = handle.volume
            for (i in 0..steps) {
                if (generation != this.generation) return false
                handle.setVolume(start + (targetLinear - start) * i / steps.toFloat())
                delay(rampStepMs)
            }
            return generation == this.generation
        }
    }

    /** Fades every given handle down to silence from its current volume. */
    suspend fun fadeOutAll(generation: Long, handles: List<PlayerHandle>, durationMs: Long): Boolean {
        mutex.withLock {
            val steps = ((durationMs + rampStepMs - 1) / rampStepMs).toInt().coerceAtLeast(1)
            val starts = handles.associateBy({ it }) { it.volume }
            for (i in 0..steps) {
                if (generation != this.generation) return false
                val t = i.toFloat() / steps
                starts.forEach { (handle, start) -> handle.setVolume(start * (1f - t)) }
                delay(rampStepMs)
            }
            return generation == this.generation
        }
    }
}
