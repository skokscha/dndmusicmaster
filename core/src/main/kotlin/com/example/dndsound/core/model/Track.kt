package com.example.dndsound.core.model

import kotlinx.serialization.Serializable

/**
 * A music file indexed from the user's library. Position is the track's home
 * on the mood wheel; uri is a SAF document URI (app never copies files).
 */
@Serializable
data class Track(
    val id: String,
    val title: String,
    val uri: String,
    val durationMs: Long,
    val mode: MusicMode = MusicMode.EXPLORATION,
    val position: WheelPoint = WheelPoint.CENTER,
    val gainDb: Float = 0f,
)
