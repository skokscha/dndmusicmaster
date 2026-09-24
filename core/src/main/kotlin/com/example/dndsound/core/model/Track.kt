package com.example.dndsound.core.model

import kotlinx.serialization.Serializable

/**
 * A music file indexed from the user's library. Position is the track's home
 * on the mood wheel (null = unplaced: unknown folder, never plays until the
 * user picks a zone); uri is a SAF document URI (app never copies files).
 */
@Serializable
data class Track(
    val id: String,
    val title: String,
    val uri: String,
    val durationMs: Long,
    val mode: MusicMode = MusicMode.EXPLORATION,
    val position: WheelPoint? = null,
    val gainDb: Float = 0f,
)
