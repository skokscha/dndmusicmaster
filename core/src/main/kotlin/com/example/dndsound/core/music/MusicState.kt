package com.example.dndsound.core.music

import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.Track
import com.example.dndsound.core.model.WheelPoint
import com.example.dndsound.core.wheel.WheelZone

/** Observable music-player state for the UI. */
data class MusicState(
    val playing: Boolean = false,
    val currentTrack: Track? = null,
    /** Wheel point that selected the current track. */
    val anchor: WheelPoint? = null,
    val mode: MusicMode = MusicMode.EXPLORATION,
    val crossfading: Boolean = false,
    val error: String? = null,
    /** Zone the marker is currently in (with hysteresis applied). */
    val targetZone: WheelZone? = null,
    /** Zone the playing track actually comes from; differs on fallback. */
    val playingZone: WheelZone? = null,
    /** True when [playingZone] differs from [targetZone] (empty target zone). */
    val isFallback: Boolean = false,
)
