package com.example.dndsound.core.music

import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.Track
import com.example.dndsound.core.model.WheelPoint

/** Observable music-player state for the UI. */
data class MusicState(
    val playing: Boolean = false,
    val currentTrack: Track? = null,
    /** Wheel point that selected the current track. */
    val anchor: WheelPoint? = null,
    val mode: MusicMode = MusicMode.EXPLORATION,
    val crossfading: Boolean = false,
    val error: String? = null,
)
