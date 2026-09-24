package com.example.dndsound.core.scheduler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RandomSpotsTest {

    @Test
    fun `interval stays within bounds`() {
        val random = Random(1)
        repeat(1000) {
            val t = RandomSpots.nextIntervalSec(10f, 30f, random)
            assertTrue(t >= 10f && t <= 30f, "interval out of bounds: $t")
        }
    }

    @Test
    fun `interval is order-safe and degenerate-safe`() {
        val random = Random(2)
        repeat(100) {
            val t = RandomSpots.nextIntervalSec(30f, 10f, random)
            assertTrue(t >= 10f && t <= 30f)
        }
        assertEquals(5f, RandomSpots.nextIntervalSec(5f, 5f, random), 1e-6f)
        assertEquals(0f, RandomSpots.nextIntervalSec(-3f, 0f, random), 1e-6f)
    }

    @Test
    fun `pickVariant never repeats the previous when alternatives exist`() {
        val random = Random(3)
        val variants = listOf("a", "b", "c")
        var previous: String? = null
        repeat(500) {
            val pick = RandomSpots.pickVariant(variants, previous, random)!!
            assertNotEquals(previous, pick, "repeated variant after previous=$previous")
            previous = pick
        }
    }

    @Test
    fun `pickVariant falls back to the only variant`() {
        assertEquals("a", RandomSpots.pickVariant(listOf("a"), "a", Random(4)))
    }

    @Test
    fun `pickVariant of empty list is null`() {
        assertNull(RandomSpots.pickVariant(emptyList(), null, Random(4)))
    }

    @Test
    fun `gain and pitch jitter stay within their bounds`() {
        val random = Random(5)
        repeat(500) {
            val gain = RandomSpots.jitterGainDb(-3f, 2f, random)
            assertTrue(gain >= -5f && gain <= -1f, "gain jitter out of bounds: $gain")
            val pitch = RandomSpots.jitterPitch(5f, random)
            assertTrue(pitch >= 0.95f && pitch <= 1.05f, "pitch jitter out of bounds: $pitch")
        }
    }
}
