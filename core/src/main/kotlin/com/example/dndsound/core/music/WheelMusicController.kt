package com.example.dndsound.core.music

import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.WheelPoint

/**
 * Hysteresis for wheel-driven track switching: once a track is playing, small
 * wheel adjustments (within [KEEP_RADIUS] of the anchor) keep it; a real
 * switch happens only when the target drifts farther away or the mode
 * changes. Anchors are per mode, so exploration and battle remember their
 * own positions.
 */
object WheelMusicController {

    const val KEEP_RADIUS: Float = 0.4f

    sealed interface Decision {
        data object Keep : Decision
        data class Switch(val anchor: WheelPoint) : Decision
    }

    fun decide(
        anchor: WheelPoint?,
        target: WheelPoint,
        requestedMode: MusicMode,
        activeMode: MusicMode,
    ): Decision = when {
        anchor == null -> Decision.Switch(target)
        requestedMode != activeMode -> Decision.Switch(target)
        anchor.distanceTo(target) > KEEP_RADIUS -> Decision.Switch(target)
        else -> Decision.Keep
    }
}
