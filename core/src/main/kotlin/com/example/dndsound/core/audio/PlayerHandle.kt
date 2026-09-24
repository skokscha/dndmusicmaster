package com.example.dndsound.core.audio

import kotlinx.coroutines.flow.SharedFlow

/**
 * One playback slot, platform-agnostic. The music engine drives exactly two
 * of these for A/B crossfading; :app provides the ExoPlayer implementation,
 * unit tests provide fakes. Volume is linear 0..1, applied per ramp step.
 */
interface PlayerHandle {

    /** Playback/state events; emissions must not block the caller. */
    val events: SharedFlow<PlayerEvent>

    /** Replaces the source and prepares it (playback starts on [play]). */
    fun setSource(uri: String)

    fun play()

    fun pause()

    /** Last value set; the engine reads it to keep interrupted fades smooth. */
    val volume: Float

    fun setVolume(linear: Float)

    /** Loop the current source from its end back to the start. */
    fun setLooping(enabled: Boolean)

    /** Playback rate multiplier (1.0 = normal); used for spot jitter. */
    fun setSpeedFactor(factor: Float)

    fun release()
}

sealed interface PlayerEvent {
    data object Ready : PlayerEvent
    data object Ended : PlayerEvent
    data class Error(val message: String?) : PlayerEvent
}
