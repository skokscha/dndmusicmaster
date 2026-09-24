package com.example.dndsound.core.wheel

import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.Track
import com.example.dndsound.core.model.WheelPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class TrackSelectorTest {

    private val center = WheelPoint.CENTER

    private fun track(id: String, x: Float, y: Float, mode: MusicMode = MusicMode.EXPLORATION) =
        Track(
            id = id,
            title = id,
            uri = "uri:$id",
            durationMs = 1000,
            mode = mode,
            position = WheelPoint(x, y),
        )

    private val library = listOf(
        track("near", 0.1f, 0.1f),
        track("mid", 0.4f, 0.0f),
        track("far", 0.95f, 0.0f),
        track("edge", 0.0f, 1.0f),
        track("battle", 0.1f, 0.1f, MusicMode.BATTLE),
    )

    @Test
    fun `empty library returns null`() {
        assertNull(TrackSelector(Random(1)).select(emptyList(), center, MusicMode.EXPLORATION))
    }

    @Test
    fun `no matching mode returns null`() {
        val explorationOnly = listOf(track("a", 0.1f, 0.1f), track("b", 0.2f, 0.2f))
        assertNull(TrackSelector(Random(1)).select(explorationOnly, center, MusicMode.BATTLE))
    }

    @Test
    fun `battle mode only selects battle tracks`() {
        val battleLibrary = listOf(
            track("b1", 0.1f, 0.1f, MusicMode.BATTLE),
            track("b2", 0.2f, 0.0f, MusicMode.BATTLE),
        )
        repeat(20) { seed ->
            val pick = TrackSelector(Random(seed)).select(battleLibrary, center, MusicMode.BATTLE)
            assertNotNull(pick)
            assertEquals(MusicMode.BATTLE, pick!!.mode)
        }
    }

    @Test
    fun `recent tracks are excluded when alternatives exist`() {
        // All exploration tracks except "far" are recent -> only "far" remains.
        val recent = listOf("near", "mid", "edge")
        val pick = TrackSelector(Random(7)).select(library, center, MusicMode.EXPLORATION, recent)
        assertEquals("far", pick!!.id)
    }

    @Test
    fun `recent exclusion respects pool size`() {
        // pool of 2: min(5, n-1) = 1 recent excluded, the other must play.
        val small = listOf(track("a", 0.1f, 0.1f), track("b", 0.9f, 0.9f))
        val pick = TrackSelector(Random(3)).select(small, center, MusicMode.EXPLORATION, listOf("a"))
        assertEquals("b", pick!!.id)
    }

    @Test
    fun `when everything is recent the full pool returns`() {
        val two = listOf(track("a", 0.1f, 0.1f), track("b", 0.2f, 0.2f))
        val pick = TrackSelector(Random(5)).select(two, center, MusicMode.EXPLORATION, listOf("a", "b"))
        assertNotNull(pick)
    }

    @Test
    fun `selection prefers the radius around the point`() {
        // Many far tracks, one near: near must win despite randomness.
        val many = (0 until 30).map { track("far$it", -0.9f, -0.9f) } + track("near", 0.1f, 0.1f)
        val selector = TrackSelector(Random(11))
        val picks = (0 until 50).mapNotNull { selector.select(many, center, MusicMode.EXPLORATION) }
        assertTrue(picks.all { it.id == "near" })
    }

    @Test
    fun `selection is deterministic for a fixed seed`() {
        val a = TrackSelector(Random(42)).select(library, center, MusicMode.EXPLORATION)
        val b = TrackSelector(Random(42)).select(library, center, MusicMode.EXPLORATION)
        assertEquals(a, b)
    }

    @Test
    fun `weighted pick never crashes on single candidate`() {
        val single = listOf(track("only", 0.1f, 0.1f))
        repeat(10) {
            assertEquals("only", TrackSelector(Random(it)).select(single, center, MusicMode.EXPLORATION)!!.id)
        }
    }
}
