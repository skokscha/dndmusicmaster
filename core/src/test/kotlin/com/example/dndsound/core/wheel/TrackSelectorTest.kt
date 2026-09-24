package com.example.dndsound.core.wheel

import com.example.dndsound.core.model.Mood
import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.Track
import com.example.dndsound.core.model.WheelPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class TrackSelectorTest {

    private val center = WheelPoint.CENTER

    /** Track parked on the mood's outer tier (radius 0.8 on its angle). */
    private fun at(mood: Mood, id: String = mood.name, radius: Float = 0.8f, mode: MusicMode = MusicMode.EXPLORATION) =
        Track(
            id = id,
            title = id,
            uri = "uri:$id",
            durationMs = 1000,
            mode = mode,
            position = polar(mood.angleDeg, radius),
        )

    private fun track(id: String, x: Float, y: Float, mode: MusicMode = MusicMode.EXPLORATION) = Track(
        id = id,
        title = id,
        uri = "uri:$id",
        durationMs = 1000,
        mode = mode,
        position = WheelPoint(x, y),
    )

    private fun pointAt(mood: Mood, radius: Float = 0.8f) = polar(mood.angleDeg, radius)

    private fun polar(angleDeg: Float, radius: Float) = WheelPoint(
        x = radius * cos(Math.toRadians(angleDeg.toDouble())).toFloat(),
        y = radius * sin(Math.toRadians(angleDeg.toDouble())).toFloat(),
    )

    private fun select(tracks: List<Track>, point: WheelPoint, mode: MusicMode = MusicMode.EXPLORATION, recent: List<String> = emptyList()) =
        TrackSelector(Random(1)).select(WheelZones.zoneAt(point), point, mode, tracks, recent)

    // ---------------------------------------------------------------- pools

    @Test
    fun `empty library returns null`() {
        assertNull(select(emptyList(), pointAt(Mood.EPIC)))
    }

    @Test
    fun `only unplaced tracks means silence`() {
        val unplaced = listOf(track("u1", 0f, 0f).copy(position = null))
        assertNull(select(unplaced, pointAt(Mood.EPIC)))
    }

    @Test
    fun `no matching mode returns null`() {
        val explorationOnly = listOf(at(Mood.SAD, "a"), at(Mood.HAPPY, "b"))
        assertNull(select(explorationOnly, pointAt(Mood.EPIC), MusicMode.BATTLE))
    }

    @Test
    fun `battle mode only selects battle tracks`() {
        val battleLibrary = listOf(
            at(Mood.SAD, "b1", mode = MusicMode.BATTLE),
            at(Mood.HAPPY, "b2", mode = MusicMode.BATTLE),
        )
        repeat(20) { seed ->
            val pick = TrackSelector(Random(seed))
                .select(WheelZones.zoneAt(center), center, MusicMode.BATTLE, battleLibrary)
            // These battle tracks live in outer sectors; the tap in the center
            // falls back to the nearest battle zone, but never leaves the mode.
            assertEquals(MusicMode.BATTLE, pick!!.track.mode)
        }
    }

    @Test
    fun `a zone tap plays only that zone's tracks`() {
        val library = listOf(at(Mood.EPIC, "e1"), at(Mood.EPIC, "e2"), at(Mood.SAD), at(Mood.HAPPY))
        val epicPoint = pointAt(Mood.EPIC)
        repeat(30) { seed ->
            val pick = TrackSelector(Random(seed))
                .select(WheelZones.zoneAt(epicPoint), epicPoint, MusicMode.EXPLORATION, library)
            assertEquals(WheelZone.Sector(Mood.EPIC, Tier.OUTER), pick!!.playingZone)
            assertFalse(pick.isFallback)
        }
    }

    @Test
    fun `tiers are separate pools`() {
        val library = listOf(at(Mood.EPIC, "inner", radius = 0.4f), at(Mood.EPIC, "outer", radius = 0.8f))
        val pick = TrackSelector(Random(7))
            .select(WheelZone.Sector(Mood.EPIC, Tier.INNER), pointAt(Mood.EPIC, 0.4f), MusicMode.EXPLORATION, library)
        assertEquals("inner", pick!!.track.id)
    }

    // ------------------------------------------------------------- fallback

    @Test
    fun `an empty target zone falls back to the nearest zone with a chip`() {
        // Sad tracks at r=0.8 (angular gap to epic ~28.5 deg) are nearer than
        // happy INNER tracks (r=0.4) and much nearer than the neutral center.
        val library = listOf(
            track("calm1", 0.1f, 0f),
            at(Mood.SAD, "s1"),
            at(Mood.HAPPY, "h1", radius = 0.5f),
        )
        val pick = TrackSelector(Random(3))
            .select(WheelZone.Sector(Mood.EPIC, Tier.OUTER), pointAt(Mood.EPIC), MusicMode.EXPLORATION, library)
        assertNotNull(pick)
        assertTrue(pick!!.isFallback, "fallback must be flagged so the UI shows the chip")
        assertEquals(WheelZone.Sector(Mood.SAD, Tier.OUTER), pick.playingZone)
        assertEquals("s1", pick.track.id)
    }

    @Test
    fun `fallback ignores recent but not the whole zone`() {
        val library = listOf(at(Mood.SAD, "s1"), at(Mood.SAD, "s2"))
        val pick = TrackSelector(Random(5))
            .select(WheelZone.Sector(Mood.EPIC, Tier.OUTER), pointAt(Mood.EPIC), MusicMode.EXPLORATION, library, listOf("s1"))
        assertTrue(pick!!.isFallback)
        assertEquals("s2", pick.track.id)
    }

    @Test
    fun `fallback never crosses modes`() {
        val library = listOf(at(Mood.SAD, "s1"))
        assertNull(
            TrackSelector(Random(5))
                .select(WheelZone.Sector(Mood.EPIC, Tier.OUTER), pointAt(Mood.EPIC), MusicMode.BATTLE, library),
        )
    }

    // --------------------------------------------------------------- recent

    @Test
    fun `recent tracks are excluded within the zone pool`() {
        val library = listOf(at(Mood.EPIC, "e1"), at(Mood.EPIC, "e2"), at(Mood.SAD, "s1"))
        val pick = TrackSelector(Random(7))
            .select(WheelZones.zoneAt(pointAt(Mood.EPIC)), pointAt(Mood.EPIC), MusicMode.EXPLORATION, library, listOf("e1"))
        assertEquals("e2", pick!!.track.id)
    }

    @Test
    fun `when everything is recent the full pool returns`() {
        val library = listOf(at(Mood.EPIC, "e1"), at(Mood.EPIC, "e2"))
        val pick = TrackSelector(Random(5))
            .select(WheelZones.zoneAt(pointAt(Mood.EPIC)), pointAt(Mood.EPIC), MusicMode.EXPLORATION, library, listOf("e1", "e2"))
        assertNotNull(pick)
        assertFalse(pick!!.isFallback)
    }

    @Test
    fun `selection is deterministic for a fixed seed`() {
        val library = listOf(at(Mood.EPIC, "e1"), at(Mood.EPIC, "e2"), at(Mood.EPIC, "e3"))
        val point = pointAt(Mood.EPIC)
        val a = TrackSelector(Random(42)).select(WheelZones.zoneAt(point), point, MusicMode.EXPLORATION, library)
        val b = TrackSelector(Random(42)).select(WheelZones.zoneAt(point), point, MusicMode.EXPLORATION, library)
        assertEquals(a, b)
    }

    @Test
    fun `weighted pick never crashes on a single candidate`() {
        val single = listOf(at(Mood.EPIC, "only"))
        repeat(10) {
            assertEquals(
                "only",
                TrackSelector(Random(it))
                    .select(WheelZones.zoneAt(pointAt(Mood.EPIC)), pointAt(Mood.EPIC), MusicMode.EXPLORATION, single)!!
                    .track.id,
            )
        }
    }

    // ------------------------------------------------------------ auto-advance

    @Test
    fun `auto-advance stays in the current track's zone`() {
        val library = listOf(at(Mood.EPIC, "e1"), at(Mood.EPIC, "e2"), at(Mood.SAD, "s1"))
        val current = at(Mood.EPIC, "e1")
        repeat(30) { seed ->
            val pick = TrackSelector(Random(seed)).next(library, current, listOf("e1"))
            assertEquals("e2", pick!!.track.id, "auto-advance jumped out of the epic zone")
            assertFalse(pick.isFallback)
        }
    }

    @Test
    fun `single-track zone replays itself after ending`() {
        val library = listOf(at(Mood.SAD, "s1"), at(Mood.EPIC, "e1"))
        val pick = TrackSelector(Random(9)).next(library, at(Mood.SAD, "s1"), listOf("s1"))
        assertEquals("s1", pick!!.track.id)
    }

    @Test
    fun `unplaced current track cannot auto-advance`() {
        val current = track("u1", 0f, 0f).copy(position = null)
        assertNull(TrackSelector(Random(1)).next(listOf(current), current))
    }
}
