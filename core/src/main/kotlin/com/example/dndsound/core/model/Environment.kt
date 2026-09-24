package com.example.dndsound.core.model

import kotlinx.serialization.Serializable

/** Ambience layers come in two flavors: seamless loops and random spot sounds. */
enum class LayerKind { LOOP, RANDOM }

/**
 * Jitter applied to random spot playback so repeated sounds do not feel
 * like copies.
 */
@Serializable
data class RandomSpec(
    val minIntervalSec: Float = 10f,
    val maxIntervalSec: Float = 30f,
    val gainJitterDb: Float = 2f,
    val panJitter: Float = 0.5f,
    val pitchJitterPct: Float = 5f,
)

/**
 * One layer of an environment: a loop the user can toggle manually, or a
 * random "spot" (birds, wolf howl) scheduled in the background.
 */
@Serializable
data class AmbienceLayer(
    val id: String,
    val name: String,
    val kind: LayerKind,
    val uris: List<String>,
    val baseGainDb: Float = 0f,
    val random: RandomSpec? = null,
    val defaultEnabled: Boolean = true,
)

/** Environment categories shown as chips in the middle panel. */
enum class EnvironmentCategory { NATURE, TOWN, VEHICLE, INTERIOR, SUPERNATURE, CUSTOM }

/** One-shot sound categories shown as chips in the right panel. */
enum class SoundCategory { NATURE, HUMAN, ANIMAL, CREATURE, ATTACK, MAGICAL, MECHANISM, CUSTOM }

/**
 * A playable environment: seamless base loop (day/night variants), manual
 * loop layers and random spot layers.
 */
@Serializable
data class Environment(
    val id: String,
    val name: String,
    val category: EnvironmentCategory = EnvironmentCategory.CUSTOM,
    val coverUri: String? = null,
    val baseDayUri: String? = null,
    val baseNightUri: String? = null,
    val layers: List<AmbienceLayer> = emptyList(),
    /**
     * True when the base loop files loop without a gap (well-encoded OGG/Opus).
     * meta.json "seamless": false switches the base to the overlapping
     * dual-player looper, which hides encoder gaps.
     */
    val seamless: Boolean = true,
    /** Duration of the main base file in ms; 0 = unknown. Drives the looper. */
    val baseDurationMs: Long = 0,
)

/**
 * A single-fire sound (goblin, sword hit). Multiple files with the same base
 * name become variants; playback picks a random one, avoiding repeats.
 */
@Serializable
data class OneShot(
    val id: String,
    val name: String,
    val category: SoundCategory = SoundCategory.CUSTOM,
    val variants: List<SoundFile> = emptyList(),
    val iconKey: String? = null,
    val gainDb: Float = 0f,
    /** Gain/pan/pitch jitter from meta.json; null = play exactly as indexed. */
    val random: RandomSpec? = null,
)
