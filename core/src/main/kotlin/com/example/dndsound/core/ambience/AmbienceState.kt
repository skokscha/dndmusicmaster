package com.example.dndsound.core.ambience

import com.example.dndsound.core.model.LayerKind
import com.example.dndsound.core.model.TimeOfDay
import com.example.dndsound.core.model.Weather

/** One environment layer as shown in the UI. */
data class LayerState(
    val id: String,
    val name: String,
    val kind: LayerKind,
    val enabled: Boolean,
    val gainDb: Float,
)

/** Observable ambience state for the UI. */
data class AmbienceState(
    val environmentId: String? = null,
    val environmentName: String? = null,
    val timeOfDay: TimeOfDay = TimeOfDay.DAY,
    val playing: Boolean = false,
    val layers: List<LayerState> = emptyList(),
    val weather: Weather = Weather.NONE,
    val weatherIntensity: Float = 0.6f,
    val error: String? = null,
)
