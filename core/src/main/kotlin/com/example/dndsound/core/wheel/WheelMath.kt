package com.example.dndsound.core.wheel

import com.example.dndsound.core.model.Mood
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Pure geometry helpers for the mood wheel. The wheel is a unit circle:
 * (0, 0) is the calm center, points are clamped to radius 1.
 */
object WheelMath {

    /** Radius below which the pointer is considered "calm". */
    const val CALM_RADIUS: Float = 0.25f

    /** Clamp a point (in unit-circle space) to the unit circle. */
    fun clampToUnitCircle(x: Float, y: Float): Pair<Float, Float> {
        val d2 = x * x + y * y
        if (d2 <= 1f) return x to y
        val d = kotlin.math.sqrt(d2)
        return x / d to y / d
    }

    /** Angle of a wheel point in degrees, in (-180, 180]. */
    fun angleDeg(x: Float, y: Float): Float =
        Math.toDegrees(atan2(y, x).toDouble()).toFloat()

    /** Nearest mood sector for an angle; angles are folded into (-180, 180]. */
    fun nearestMood(angleDeg: Float): Mood {
        val a = normalize(angleDeg)
        return Mood.entries.minBy { mood -> angularDistance(a, mood.angleDeg) }
    }

    /**
     * Intensity of a wheel point: 0 in the calm center, 1 at the rim.
     * The calm zone maps to 0.
     */
    fun intensity(r: Float): Float =
        when {
            r <= CALM_RADIUS -> 0f
            r >= 1f -> 1f
            else -> (r - CALM_RADIUS) / (1f - CALM_RADIUS)
        }

    /** Angular distance between two angles in degrees, in [0, 180]. */
    fun angularDistance(a: Float, b: Float): Float {
        val d = abs(normalize(a) - normalize(b))
        return if (d > 180f) 360f - d else d
    }

    /** Fold an angle into (-180, 180]. */
    fun normalize(angleDeg: Float): Float {
        var a = angleDeg % 360f
        if (a <= -180f) a += 360f
        if (a > 180f) a -= 360f
        return a
    }
}
