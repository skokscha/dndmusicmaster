package com.example.dndsound.core.scheduler

import kotlin.math.abs
import kotlin.random.Random

/**
 * Pure scheduling decisions for random spot layers (birds, wolf howls…).
 * The engine owns the coroutine/timing; this object owns the "when" and
 * "which variant" logic so it can be unit tested.
 */
object RandomSpots {

    /** Random wait in seconds within [minSec, maxSec] (order-safe, >= 0). */
    fun nextIntervalSec(minSec: Float, maxSec: Float, random: Random = Random.Default): Float {
        val lo = minOf(minSec, maxSec).coerceAtLeast(0f)
        val hi = maxOf(minSec, maxSec).coerceAtLeast(lo)
        return if (hi == lo) lo else lo + random.nextFloat() * (hi - lo)
    }

    /**
     * Picks a variant different from [previous] when at least one alternative
     * exists. Returns null for an empty list.
     */
    fun <T> pickVariant(variants: List<T>, previous: T?, random: Random = Random.Default): T? {
        if (variants.isEmpty()) return null
        val pool = if (variants.size == 1) variants else variants.filter { it != previous }
        return pool[random.nextInt(pool.size)]
    }

    /** Jittered gain in dB around [baseDb] by ±[jitterDb]. */
    fun jitterGainDb(baseDb: Float, jitterDb: Float, random: Random = Random.Default): Float =
        baseDb + (random.nextFloat() * 2f - 1f) * abs(jitterDb)

    /** Jittered pitch factor around 1.0 by ±[jitterPct] (e.g. 0.95..1.05 for 5). */
    fun jitterPitch(jitterPct: Float, random: Random = Random.Default): Float =
        1f + (random.nextFloat() * 2f - 1f) * (jitterPct / 100f)
}
