package com.example.dndsound.core.model

import kotlinx.serialization.Serializable

/** Mixer buses; all gains are stored in dB and converted to linear per player. */
enum class Bus { MASTER, MUSIC, AMBIENCE, SFX }

/** One named bus gain; used instead of Map<Bus, Float> for stable JSON. */
@Serializable
data class BusGain(val bus: Bus, val gainDb: Float)

/** Full playable state of the three app parts, captured as a scene. */
@Serializable
data class SceneSnapshot(
    val musicPoint: WheelPoint = WheelPoint.CENTER,
    val musicMode: MusicMode = MusicMode.EXPLORATION,
    val environmentId: String? = null,
    val timeOfDay: TimeOfDay = TimeOfDay.DAY,
    val enabledLayerIds: Set<String> = emptySet(),
    val layerGainsDb: Map<String, Float> = emptyMap(),
    val weather: Weather = Weather.NONE,
    val weatherIntensity: Float = 0f,
    val busGains: List<BusGain> = listOf(
        BusGain(Bus.MASTER, 0f),
        BusGain(Bus.MUSIC, 0f),
        BusGain(Bus.AMBIENCE, 0f),
        BusGain(Bus.SFX, 0f),
    ),
) {
    fun busGain(bus: Bus): Float =
        busGains.firstOrNull { it.bus == bus }?.gainDb ?: 0f
}

/** A named preset the user saved, one tap to restore. */
@Serializable
data class Scene(
    val id: String,
    val name: String,
    val snapshot: SceneSnapshot,
)
