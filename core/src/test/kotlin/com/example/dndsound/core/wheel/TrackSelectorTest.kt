package com.example.dndsound.core.wheel

import com.example.dndsound.core.model.Mood
import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.Track
import com.example.dndsound.core.model.WheelPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class TrackSelectorTest {

    private val center = WheelPoint.CENTER

    /** Track parked at the mood's home point (radius 0.7 on its angle). */
    private fun at(mood: Mood, id: String = mood.name, radius: Float = 0.7f) = Track(
        id = id,
        title = id,
        uri = "uri:$id",
        durationMs = 1000,
        position = WheelPoint(
            x = radius * cos(Math.toRadians(mood.angleDeg.toDouble())).toFloat(),
            y = radius * sin(Math.toRadians(mood.angleDeg.toDouble())).toFloat(),
        ),
    )

    private fun track(id: String, x: Float, y: Float, mode: MusicMode = MusicMode.EXPLORATION) =
        Track(
            id = id,
            title = id,
            uri = "uri:$id",
            durationMs = 1000,
            mode = mode,
            position = WheelPoint(x, y),
        )

    private fun pointAt(mood: Mood, radius: Float = 0.9f) = WheelPoint(
        x = radius * cos(Math.toRadians(mood.angleDeg.toDouble())).toFloat(),
        y = radius * sin(Math.toRadians(mood.angleDeg.toDouble())).toFloat(),
    )

    private val calm = track("calm1", 0.1f, 0.0f)

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
    fun `a sector tap plays only that sector's tracks`() {
        // Tap deep in the epic sector: every pick must be an epic track, even
        // though calm/sad/happy tracks sit closer to... nothing — strict rule.
        val library = listOf(at(Mood.EPIC, "e1"), at(Mood.EPIC, "e2"), calm, at(Mood.SAD), at(Mood.HAPPY))
        val epicPoint = pointAt(Mood.EPIC)
        repeat(30) { seed ->
            val pick = TrackSelector(Random(seed)).select(library, epicPoint, MusicMode.EXPLORATION)
            assertEquals(Mood.EPIC, WheelMath.nearestMood(pick!!.position.angleDeg))
        }
    }

    @Test
    fun `an empty sector stays silent — no leakage from other folders`() {
        // The key regression: no epic tracks at all -> nothing plays, even
        // though sad/calm/happy tracks exist.
        val library = listOf(calm, at(Mood.SAD), at(Mood.HAPPY))
        repeat(20) { seed ->
            assertNull(TrackSelector(Random(seed)).select(library, pointAt(Mood.EPIC), MusicMode.EXPLORATION))
        }
    }

    @Test
    fun `calm center plays calm tracks only`() {
        val library = listOf(calm, track("calm2", 0.0f, 0.2f), at(Mood.HAPPY), at(Mood.CREEPY))
        repeat(30) { seed ->
            val pick = TrackSelector(Random(seed)).select(library, center, MusicMode.EXPLORATION)
            assertTrue(pick!!.position.isCalm(), "non-calm track leaked into the calm zone")
        }
        // Without any calm tracks the center stays silent.
        val noCalm = listOf(at(Mood.HAPPY), at(Mood.CREEPY))
        assertNull(TrackSelector(Random(3)).select(noCalm, center, MusicMode.EXPLORATION))
    }

    @Test
    fun `recent tracks are excluded within the sector pool`() {
        val library = listOf(at(Mood.EPIC, "e1"), at(Mood.EPIC, "e2"), at(Mood.SAD, "s1"))
        // e1 is recent; the only remaining epic track is e2.
        val pick = TrackSelector(Random(7)).select(library, pointAt(Mood.EPIC), MusicMode.EXPLORATION, listOf("e1"))
        assertEquals("e2", pick!!.id)
    }

    @Test
    fun `recent exclusion respects pool size`() {
        // Sector pool of 2: min(5, n-1) = 1 recent excluded, the other plays.
        val library = listOf(at(Mood.EPIC, "e1"), at(Mood.EPIC, "e2"), at(Mood.SAD, "s1"))
        val pick = TrackSelector(Random(3)).select(library, pointAt(Mood.EPIC), MusicMode.EXPLORATION, listOf("e1"))
        assertEquals("e2", pick!!.id)
    }

    @Test
    fun `when everything is recent the full pool returns`() {
        val library = listOf(at(Mood.EPIC, "e1"), at(Mood.EPIC, "e2"))
        val pick = TrackSelector(Random(5)).select(
            library,
            pointAt(Mood.EPIC),
            MusicMode.EXPLORATION,
            listOf("e1", "e2"),
        )
        assertNotNull(pick)
    }

    @Test
    fun `selection is deterministic for a fixed seed`() {
        val library = listOf(at(Mood.EPIC, "e1"), at(Mood.EPIC, "e2"), at(Mood.EPIC, "e3"), calm)
        val a = TrackSelector(Random(42)).select(library, pointAt(Mood.EPIC), MusicMode.EXPLORATION)
        val b = TrackSelector(Random(42)).select(library, pointAt(Mood.EPIC), MusicMode.EXPLORATION)
        assertEquals(a, b)
    }

    @Test
    fun `weighted pick never crashes on single candidate`() {
        val single = listOf(at(Mood.EPIC, "only"))
        repeat(10) {
            assertEquals(
                "only",
                TrackSelector(Random(it)).select(single, pointAt(Mood.EPIC), MusicMode.EXPLORATION)!!.id,
            )
        }
    }

    @Test
    fun `auto-advance stays in the current track's sector`() {
        val library = listOf(at(Mood.EPIC, "e1"), at(Mood.EPIC, "e2"), at(Mood.SAD, "s1"), calm)
        val current = at(Mood.EPIC, "e1")
        repeat(30) { seed ->
            val pick = TrackSelector(Random(seed)).next(library, current, listOf("e1"))
            assertEquals("e2", pick!!.id, "auto-advance jumped out of the epic sector")
        }
    }

    @Test
    fun `single-track sector replays itself after ending`() {
        val library = listOf(at(Mood.SAD, "s1"), at(Mood.EPIC, "e1"))
        val pick = TrackSelector(Random(9)).next(library, at(Mood.SAD, "s1"), listOf("s1"))
        assertEquals("s1", pick!!.id)
    }
}
