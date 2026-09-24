package com.example.dndsound.core.wheel

import com.example.dndsound.core.model.Mood
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class WheelMathTest {

    @Test
    fun `sector centers map to their moods`() {
        assertEquals(Mood.SAD, WheelMath.nearestMood(0f))
        assertEquals(Mood.EPIC, WheelMath.nearestMood(45f))
        assertEquals(Mood.HAPPY, WheelMath.nearestMood(90f))
        assertEquals(Mood.FUNNY, WheelMath.nearestMood(135f))
        assertEquals(Mood.MAGICAL, WheelMath.nearestMood(180f))
        assertEquals(Mood.MYSTIC, WheelMath.nearestMood(-135f))
        assertEquals(Mood.CREEPY, WheelMath.nearestMood(-90f))
        assertEquals(Mood.TENSE, WheelMath.nearestMood(-45f))
    }

    @Test
    fun `points inside a sector resolve to its mood`() {
        // Sector half-width is 22.5 degrees; check points away from borders.
        assertEquals(Mood.EPIC, WheelMath.nearestMood(60f))
        assertEquals(Mood.FUNNY, WheelMath.nearestMood(130f))
        assertEquals(Mood.HAPPY, WheelMath.nearestMood(110f))
        assertEquals(Mood.SAD, WheelMath.nearestMood(-20f))
    }

    @Test
    fun `normalize folds angles into the expected range`() {
        assertEquals(0f, WheelMath.normalize(360f))
        assertEquals(180f, WheelMath.normalize(-180f))
        assertEquals(-90f, WheelMath.normalize(270f))
        assertEquals(45f, WheelMath.normalize(405f))
        assertEquals(0f, WheelMath.normalize(0f))
    }

    @Test
    fun `clamp keeps inner points and pulls outer points onto the rim`() {
        assertEquals(0.5f to -0.5f, WheelMath.clampToUnitCircle(0.5f, -0.5f))

        val (rx, ry) = WheelMath.clampToUnitCircle(2f, 0f)
        assertEquals(1f, rx, 1e-6f)
        assertEquals(0f, ry, 1e-6f)

        val (dx, dy) = WheelMath.clampToUnitCircle(1f, 1f)
        assertEquals(sqrt(0.5).toFloat(), dx, 1e-6f)
        assertEquals(sqrt(0.5).toFloat(), dy, 1e-6f)
    }

    @Test
    fun `intensity is zero in the calm zone and grows to the rim`() {
        assertEquals(0f, WheelMath.intensity(0f))
        assertEquals(0f, WheelMath.intensity(WheelMath.CALM_RADIUS))
        assertEquals(1f, WheelMath.intensity(1f))
        assertEquals(1f, WheelMath.intensity(5f))
        assertEquals(0.5f, WheelMath.intensity(0.625f), 1e-6f)
    }

    @Test
    fun `angular distance wraps around the circle`() {
        assertEquals(90f, WheelMath.angularDistance(170f, -100f), 1e-4f)
        assertEquals(0f, WheelMath.angularDistance(-180f, 180f), 1e-4f)
        assertEquals(45f, WheelMath.angularDistance(45f, 90f), 1e-4f)
    }
}
