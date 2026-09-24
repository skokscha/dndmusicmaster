package com.example.dndsound.core.library

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Optional `meta.json` placed next to audio files to override the automatic
 * indexing (see docs/CONTENT.md). All fields are optional; unknown keys are
 * ignored so the schema can grow without breaking old files.
 */
@Serializable
data class MetaJson(
    val title: String? = null,
    val category: String? = null,
    val icon: String? = null,
    val mood: String? = null,
    val mode: String? = null,
    val wheel: Wheel? = null,
    val gainDb: Float? = null,
    val random: Random? = null,
    /** meta-only: false switches the environment base to the overlap looper. */
    val seamless: Boolean? = null,
) {
    @Serializable
    data class Wheel(val x: Float? = null, val y: Float? = null)

    @Serializable
    data class Random(
        val minIntervalSec: Float? = null,
        val maxIntervalSec: Float? = null,
        val gainJitterDb: Float? = null,
        val panJitter: Float? = null,
        val pitchJitterPct: Float? = null,
    )

    /** Normalize into the model; null here means "use the default spec". */
    fun toRandomSpec(): com.example.dndsound.core.model.RandomSpec? {
        val r = random ?: return null
        return com.example.dndsound.core.model.RandomSpec(
            minIntervalSec = r.minIntervalSec ?: 10f,
            maxIntervalSec = r.maxIntervalSec ?: 30f,
            gainJitterDb = r.gainJitterDb ?: 2f,
            panJitter = r.panJitter ?: 0.5f,
            pitchJitterPct = r.pitchJitterPct ?: 5f,
        )
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** Parses meta.json text; returns null for empty or malformed content. */
fun parseMetaJson(text: String): MetaJson? {
    if (text.isBlank()) return null
    return try {
        json.decodeFromString<MetaJson>(text)
    } catch (_: kotlinx.serialization.SerializationException) {
        null
    }
}
