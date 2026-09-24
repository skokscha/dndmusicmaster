package com.example.dndsound.core.model

import com.example.dndsound.core.wheel.WheelMath
import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * A point inside the unit mood wheel. (0, 0) is the neutral center;
 * the rim is radius 1. Zone classification lives in WheelZones.
 */
@Serializable
data class WheelPoint(val x: Float, val y: Float) {

    val radius: Float get() = sqrt(x * x + y * y)

    val angleDeg: Float get() = WheelMath.angleDeg(x, y)

    /** Euclidean distance to another wheel point. */
    fun distanceTo(other: WheelPoint): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    /** A copy pulled onto the unit circle if it lies outside. */
    fun clamped(): WheelPoint {
        val (cx, cy) = WheelMath.clampToUnitCircle(x, y)
        return WheelPoint(cx, cy)
    }

    companion object {
        val CENTER = WheelPoint(0f, 0f)
    }
}
