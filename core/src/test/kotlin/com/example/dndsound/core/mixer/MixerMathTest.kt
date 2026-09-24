package com.example.dndsound.core.mixer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MixerMathTest {

    @Test
    fun `db to linear and back round-trips`() {
        for (db in floatArrayOf(0f, -6f, -12f, -30f, -59f)) {
            assertEquals(db, GainMath.linearToDb(GainMath.dbToLinear(db)), 1e-3f)
        }
    }

    @Test
    fun `zero db is unity gain`() {
        assertEquals(1f, GainMath.dbToLinear(0f), 1e-6f)
    }

    @Test
    fun `silence floor maps to zero linear`() {
        assertEquals(0f, GainMath.dbToLinear(GainMath.SILENCE_DB))
        assertEquals(0f, GainMath.dbToLinear(-120f))
        assertEquals(GainMath.SILENCE_DB, GainMath.linearToDb(0f))
    }

    @Test
    fun `clamp keeps gains in range`() {
        assertEquals(GainMath.SILENCE_DB, GainMath.clampDb(-100f))
        assertEquals(GainMath.MAX_DB, GainMath.clampDb(50f))
        assertEquals(-3f, GainMath.clampDb(-3f))
    }

    @Test
    fun `equal-power curves keep constant power`() {
        var t = 0f
        while (t <= 1f) {
            assertEquals(1f, Crossfade.powerSum(t), 1e-3f, "power sum off at t=$t")
            t += 0.05f
        }
    }

    @Test
    fun `crossfade endpoints are silence and unity`() {
        assertEquals(1f, Crossfade.fadeOut(0f), 1e-6f)
        assertEquals(0f, Crossfade.fadeOut(1f), 1e-6f)
        assertEquals(0f, Crossfade.fadeIn(0f), 1e-6f)
        assertEquals(1f, Crossfade.fadeIn(1f), 1e-6f)
    }

    @Test
    fun `ramp includes both endpoints`() {
        val gains = GainRamp.linearGains(-60f, 0f, durationMs = 1000)
        assertEquals(0f, gains.first(), 1e-6f)
        assertEquals(1f, gains.last(), 1e-6f)
    }

    @Test
    fun `ramp step count matches duration`() {
        // 1000 ms / 25 ms = 40 steps -> 41 samples including both endpoints.
        val gains = GainRamp.linearGains(-60f, 0f, durationMs = 1000)
        assertEquals(41, gains.size)
    }

    @Test
    fun `instant ramp is a single sample`() {
        val gains = GainRamp.linearGains(-30f, 0f, durationMs = 0)
        assertEquals(1, gains.size)
        assertEquals(GainMath.dbToLinear(0f), gains.first(), 1e-6f)
    }

    @Test
    fun `ramp is monotonic in the linear domain for a fade-in`() {
        val gains = GainRamp.linearGains(-60f, 0f, durationMs = 500)
        assertTrue(gains.zipWithNext().all { (a, b) -> a <= b })
    }

    @Test
    fun `pan endpoints are single-channel and center is symmetric`() {
        val (leftAtLeft, rightAtLeft) = PanMath.volumes(-1f)
        assertEquals(1f, leftAtLeft, 1e-6f)
        assertEquals(0f, rightAtLeft, 1e-6f)
        val (leftAtRight, rightAtRight) = PanMath.volumes(1f)
        assertEquals(0f, leftAtRight, 1e-6f)
        assertEquals(1f, rightAtRight, 1e-6f)
        val (left, right) = PanMath.volumes(0f)
        assertEquals(left, right, 1e-6f)
    }

    @Test
    fun `pan keeps constant power across the sweep`() {
        var pan = -1f
        while (pan <= 1f) {
            val (left, right) = PanMath.volumes(pan)
            assertEquals(1f, left * left + right * right, 1e-3f, "power off at pan=$pan")
            pan += 0.1f
        }
    }
}
