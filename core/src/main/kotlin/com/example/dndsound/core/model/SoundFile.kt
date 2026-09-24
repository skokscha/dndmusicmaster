package com.example.dndsound.core.model

import kotlinx.serialization.Serializable

/**
 * A single audio file outside the music wheel — currently weather loops.
 * Weather playback picks from the list of files for the chosen weather.
 */
@Serializable
data class SoundFile(
    val id: String,
    val title: String,
    val uri: String,
    val durationMs: Long = 0L,
    val gainDb: Float = 0f,
)
