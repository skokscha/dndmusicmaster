package com.example.dndsound.core.audio

import com.example.dndsound.core.model.Bus
import com.example.dndsound.core.model.Environment
import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.OneShot
import com.example.dndsound.core.model.TimeOfDay
import com.example.dndsound.core.model.Track
import com.example.dndsound.core.model.Weather
import com.example.dndsound.core.model.WheelPoint
import com.example.dndsound.core.model.WheelPoint.Companion.CENTER
import com.example.dndsound.core.wheel.WheelMath
import kotlinx.coroutines.flow.StateFlow

/** Observable state of the whole audio engine. */
data class EngineState(
    val playing: Boolean = false,
    val musicPoint: WheelPoint = CENTER,
    val musicMode: MusicMode = MusicMode.EXPLORATION,
    val currentTrack: Track? = null,
    val environmentId: String? = null,
    val timeOfDay: TimeOfDay = TimeOfDay.DAY,
    val weather: Weather = Weather.NONE,
    val weatherIntensity: Float = 0f,
    val enabledLayerIds: Set<String> = emptySet(),
    val busGainsDb: Map<Bus, Float> = mapOf(
        Bus.MASTER to 0f,
        Bus.MUSIC to 0f,
        Bus.AMBIENCE to 0f,
        Bus.SFX to 0f,
    ),
    val libraryEmpty: Boolean = false,
) {
    /** Label under the wheel: "happy", or "calm" near the center. */
    val moodLabel: String?
        get() = when {
            musicPoint.isCalm() -> "calm"
            else -> WheelMath.nearestMood(musicPoint.angleDeg).name.lowercase()
        }
}

/**
 * Platform-neutral contract of the audio engine. :core defines it, :app
 * implements it with Media3 (music, long loops) and SoundPool (one-shots,
 * random spots).
 */
interface AudioEngine {
    val state: StateFlow<EngineState>

    fun setMusicTarget(point: WheelPoint, mode: MusicMode)
    fun nextTrack()
    fun setEnvironment(env: Environment?, timeOfDay: TimeOfDay)
    fun setLayerEnabled(layerId: String, enabled: Boolean)
    fun setLayerGain(layerId: String, gain: Float)
    fun setWeather(weather: Weather, intensity: Float)
    fun playOneShot(sound: OneShot)
    fun stopAllOneShots()
    fun setBusGain(bus: Bus, gainDb: Float)
    fun pauseAll()
    fun resumeAll()
    fun release()
}
