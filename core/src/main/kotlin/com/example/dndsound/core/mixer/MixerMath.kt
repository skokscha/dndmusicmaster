package com.example.dndsound.core.mixer

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * dB <-> linear gain conversion. Gains below [SILENCE_DB] are treated as
 * full silence; +6 dB is the soft ceiling (buses can still exceed it).
 */
object GainMath {

    const val SILENCE_DB: Float = -60f

    fun dbToLinear(db: Float): Float =
        if (db <= SILENCE_DB) 0f else 10f.pow(db / 20f)

    fun linearToDb(linear: Float): Float =
        when {
            linear <= 0f -> SILENCE_DB
            else -> maxOf(SILENCE_DB, 20f * kotlin.math.log10(linear))
        }

    /** Clamp a dB gain into [SILENCE_DB, MAX_DB]. */
    fun clampDb(db: Float): Float = db.coerceIn(SILENCE_DB, MAX_DB)

    const val MAX_DB: Float = 6f
}

/**
 * Equal-power crossfade curves: at progress t in [0, 1] the outgoing player
 * uses fadeOut(t) and the incoming fadeIn(t); the sum of squared gains is
 * ~1, so perceived loudness stays constant.
 */
object Crossfade {

    fun fadeOut(t: Float): Float = cos(t * (Math.PI / 2).toFloat())

    fun fadeIn(t: Float): Float = sin(t * (Math.PI / 2).toFloat())

    /** Power sum of both curves — should stay close to 1 across the fade. */
    fun powerSum(t: Float): Float {
        val out = fadeOut(t)
        val inn = fadeIn(t)
        return out * out + inn * inn
    }
}

/**
 * Pure gain ramp: a perceptual (dB-domain) ramp from start to target, sampled
 * every [stepMs]. The engine plays these steps from a coroutine; here it is
 * fully deterministic and testable.
 */
object GainRamp {

    const val DEFAULT_STEP_MS: Long = 25L

    /**
     * Returns linear gains (including both endpoints) for a ramp.
     * durationMs <= 0 yields a single target sample.
     */
    fun linearGains(
        startDb: Float,
        targetDb: Float,
        durationMs: Long,
        stepMs: Long = DEFAULT_STEP_MS,
    ): List<Float> {
        if (durationMs <= 0 || abs(targetDb - startDb) < 1e-4f) {
            return listOf(GainMath.dbToLinear(targetDb))
        }
        val steps = ((durationMs + stepMs - 1) / stepMs).toInt().coerceAtLeast(1)
        val gains = ArrayList<Float>(steps + 1)
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val db = startDb + (targetDb - startDb) * t
            gains.add(GainMath.dbToLinear(db))
        }
        return gains
    }

    /** Delay in ms between ramp steps. */
    fun stepDelayMs(stepMs: Long = DEFAULT_STEP_MS): Long = stepMs
}
