package com.example.dndsound.core.wheel

import com.example.dndsound.core.model.Mood
import com.example.dndsound.core.model.WheelPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class WheelZonesTest {

    private val cfg = WheelConfig()

    private fun pointAt(angleDeg: Float, radius: Float) = WheelPoint(
        x = radius * cos(Math.toRadians(angleDeg.toDouble())).toFloat(),
        y = radius * sin(Math.toRadians(angleDeg.toDouble())).toFloat(),
    )

    // ---------------------------------------------------------------- zoneAt

    @Test
    fun `center and inner core are neutral`() {
        assertEquals(WheelZone.Neutral, WheelZones.zoneAt(WheelPoint.CENTER))
        assertEquals(WheelZone.Neutral, WheelZones.zoneAt(pointAt(123f, 0.19f)))
    }

    @Test
    fun `exactly on the center radius already belongs to a sector`() {
        // r < centerRadius is neutral, so r == centerRadius is a sector.
        assertEquals(WheelZone.Sector(Mood.SAD, Tier.INNER), WheelZones.zoneAt(pointAt(0f, cfg.centerRadius)))
    }

    @Test
    fun `sector centers resolve to inner and outer tiers`() {
        assertEquals(WheelZone.Sector(Mood.EPIC, Tier.INNER), WheelZones.zoneAt(pointAt(45f, 0.4f)))
        assertEquals(WheelZone.Sector(Mood.EPIC, Tier.OUTER), WheelZones.zoneAt(pointAt(45f, 0.6f)))
        assertEquals(WheelZone.Sector(Mood.EPIC, Tier.OUTER), WheelZones.zoneAt(pointAt(45f, 1f)))
        assertEquals(WheelZone.Sector(Mood.EPIC, Tier.INNER), WheelZones.zoneAt(pointAt(45f, 0.59f)))
    }

    @Test
    fun `all eight sector centers classify at both tiers`() {
        Mood.entries.forEach { mood ->
            assertEquals(WheelZone.Sector(mood, Tier.INNER), WheelZones.zoneAt(pointAt(mood.angleDeg, 0.35f)))
            assertEquals(WheelZone.Sector(mood, Tier.OUTER), WheelZones.zoneAt(pointAt(mood.angleDeg, 0.85f)))
        }
    }

    @Test
    fun `boundary band is a transition with its palette id`() {
        // happy/epic boundary at 67.5 degrees -> victory.
        val victory = WheelZones.zoneAt(pointAt(67.5f, 0.5f))
        assertEquals("transition.victory", victory.id)
        // Half-width edges: +-6 degrees around the boundary.
        assertEquals(victory.id, WheelZones.zoneAt(pointAt(67.5f - cfg.transitionHalfWidthDeg, 0.5f)).id)
        assertEquals(victory.id, WheelZones.zoneAt(pointAt(67.5f + cfg.transitionHalfWidthDeg, 0.5f)).id)
        // Just beyond the band the sectors take over.
        assertEquals(
            WheelZone.Sector(Mood.EPIC, Tier.INNER),
            WheelZones.zoneAt(pointAt(67.5f - cfg.transitionHalfWidthDeg - 0.5f, 0.5f)),
        )
        assertEquals(
            WheelZone.Sector(Mood.HAPPY, Tier.INNER),
            WheelZones.zoneAt(pointAt(67.5f + cfg.transitionHalfWidthDeg + 0.5f, 0.5f)),
        )
    }

    @Test
    fun `transitions span the full ring through the wrap`() {
        // magical(180) <-> funny(135) boundary at 157.5 -> whimsical.
        assertEquals("transition.whimsical", WheelZones.zoneAt(pointAt(157.5f, 0.8f)).id)
        // tense(-45) <-> creepy(-90) boundary at -67.5 -> dread.
        assertEquals("transition.dread", WheelZones.zoneAt(pointAt(-67.5f, 0.3f)).id)
        assertEquals("transition.dread", WheelZones.zoneAt(pointAt(292.5f, 0.3f)).id)
        // creepy(-90) <-> mystic(-135) boundary at -112.5 -> haunted.
        assertEquals("transition.haunted", WheelZones.zoneAt(pointAt(-112.5f, 0.3f)).id)
    }

    @Test
    fun `angles near 180 degrees stay with magical`() {
        assertEquals(WheelZone.Sector(Mood.MAGICAL, Tier.OUTER), WheelZones.zoneAt(pointAt(179f, 0.9f)))
        assertEquals(WheelZone.Sector(Mood.MAGICAL, Tier.OUTER), WheelZones.zoneAt(pointAt(-179f, 0.9f)))
    }

    // ------------------------------------------------------------ hysteresis

    @Test
    fun `marker jitter within hysteresis keeps the previous zone`() {
        val previous = WheelZone.Sector(Mood.EPIC, Tier.OUTER)
        // 0.5 degrees beyond the clear-zone edge: distance ~0.007 < 0.03.
        val jittered = pointAt(28f, 0.8f)
        assertEquals(
            previous,
            WheelZones.zoneAtWithHysteresis(jittered, previous, cfg),
            "zone must not flip while the marker drifts at the boundary",
        )
    }

    @Test
    fun `deeper drift crosses into the new zone`() {
        val previous = WheelZone.Sector(Mood.EPIC, Tier.OUTER)
        // 2.5 degrees beyond the edge: distance ~0.035 > 0.03.
        val drifted = pointAt(26f, 0.8f)
        val zone = WheelZones.zoneAtWithHysteresis(drifted, previous, cfg)
        assertTrue(zone is WheelZone.Transition, "expected the transition band, got $zone")
    }

    @Test
    fun `hysteresis never applies without a previous zone`() {
        val p = pointAt(28f, 0.8f)
        assertEquals(WheelZones.zoneAt(p), WheelZones.zoneAtWithHysteresis(p, null, cfg))
    }

    // ------------------------------------------------------- distanceToZone

    @Test
    fun `distance is zero inside the zone`() {
        val epic = WheelZone.Sector(Mood.EPIC, Tier.OUTER)
        assertEquals(0f, WheelZones.distanceToZone(pointAt(45f, 0.8f), epic), 1e-5f)
        assertEquals(0f, WheelZones.distanceToZone(pointAt(30f, 0.7f), epic), 1e-5f)
        assertEquals(0f, WheelZones.distanceToZone(WheelPoint.CENTER, WheelZone.Neutral), 1e-5f)
        assertEquals(
            0f,
            WheelZones.distanceToZone(pointAt(67.5f, 0.4f), WheelZones.zoneAt(pointAt(67.5f, 0.4f))),
            1e-5f,
        )
    }

    @Test
    fun `distance to neutral grows with radius past the center`() {
        assertEquals(0.3f, WheelZones.distanceToZone(pointAt(10f, 0.5f), WheelZone.Neutral), 1e-5f)
        assertEquals(0f, WheelZones.distanceToZone(pointAt(10f, 0.1f), WheelZone.Neutral), 1e-5f)
    }

    @Test
    fun `distance from the center to a sector equals the inner radius bound`() {
        assertEquals(0.2f, WheelZones.distanceToZone(WheelPoint.CENTER, WheelZone.Sector(Mood.EPIC, Tier.INNER)), 1e-4f)
    }

    // ---------------------------------------------------- default positions

    @Test
    fun `default positions always land in their own zone`() {
        val random = Random(20260925)
        WheelZonePalette.zoneIds.forEach { id ->
            val zone = WheelZonePalette.zoneById(id)!!
            repeat(1000) { i ->
                val p = WheelZonePalette.defaultPositionFor(zone, "track-$id-$i-${random.nextInt()}")
                assertEquals(zone, WheelZones.zoneAt(p), "seed $i escaped zone $id: $p")
            }
        }
    }

    @Test
    fun `default positions are deterministic per seed`() {
        val zone = WheelZone.Sector(Mood.HAPPY, Tier.OUTER)
        assertEquals(
            WheelZonePalette.defaultPositionFor(zone, "music/happy/vivid-town/theme_a.ogg"),
            WheelZonePalette.defaultPositionFor(zone, "music/happy/vivid-town/theme_a.ogg"),
        )
    }
}

class WheelZonePaletteTest {

    @Test
    fun `palette defines 25 zones`() {
        assertEquals(25, WheelZonePalette.zoneIds.size)
    }

    @Test
    fun `sector ids follow the mood-dot-tier convention`() {
        assertEquals("happy.vivid_town", WheelZonePalette.sectorId(Mood.HAPPY, Tier.OUTER))
        assertEquals("happy.calm", WheelZonePalette.sectorId(Mood.HAPPY, Tier.INNER))
        assertEquals("magical.slight_magic", WheelZonePalette.sectorId(Mood.MAGICAL, Tier.INNER))
    }

    @Test
    fun `zoneById is the inverse of zone id`() {
        assertNotNull(WheelZonePalette.zoneById("neutral"))
        assertEquals(WheelZone.Sector(Mood.HAPPY, Tier.OUTER), WheelZonePalette.zoneById("happy.vivid_town"))
        assertEquals(WheelZone.Sector(Mood.CREEPY, Tier.INNER), WheelZonePalette.zoneById("creepy.eerie"))
        val transition = WheelZonePalette.zoneById("transition.tragic_fight")
        assertTrue(transition is WheelZone.Transition && transition.a == Mood.EPIC)
        assertNull(WheelZonePalette.zoneById("nope.gone"))
    }

    @Test
    fun `folder mapping covers tiers, case and separators`() {
        assertEquals(
            WheelZone.Sector(Mood.HAPPY, Tier.OUTER),
            WheelZonePalette.zoneForFolder("music/happy/vivid-town")!!.zone,
        )
        assertEquals(
            WheelZone.Sector(Mood.HAPPY, Tier.OUTER),
            WheelZonePalette.zoneForFolder("MUSIC/Happy/Vivid_Town")!!.zone,
        )
        assertEquals(
            WheelZone.Sector(Mood.HAPPY, Tier.INNER),
            WheelZonePalette.zoneForFolder("music/happy/calm")!!.zone,
        )
        assertEquals(
            WheelZone.Sector(Mood.EPIC, Tier.OUTER),
            WheelZonePalette.zoneForFolder("music/epic")!!.zone,
        )
        assertEquals(
            WheelZone.Neutral,
            WheelZonePalette.zoneForFolder("music/neutral")!!.zone,
        )
        assertEquals(
            "transition.victory",
            WheelZonePalette.zoneForFolder("music/transitions/victory")!!.zone.id,
        )
        assertEquals(
            "transition.tragic_fight",
            WheelZonePalette.zoneForFolder("music/transitions/tragic-fight")!!.zone.id,
        )
    }

    @Test
    fun `battle prefix switches the mode`() {
        val match = WheelZonePalette.zoneForFolder("music/battle/creepy/creepy")!!
        assertEquals(com.example.dndsound.core.model.MusicMode.BATTLE, match.mode)
        assertEquals(WheelZone.Sector(Mood.CREEPY, Tier.OUTER), match.zone)
        assertEquals(
            com.example.dndsound.core.model.MusicMode.BATTLE,
            WheelZonePalette.zoneForFolder("music/battle/transitions/dread")!!.mode,
        )
    }

    @Test
    fun `unknown folders return null`() {
        assertNull(WheelZonePalette.zoneForFolder("music/oops"))
        assertNull(WheelZonePalette.zoneForFolder("music"))
        assertNull(WheelZonePalette.zoneForFolder("music/battle"))
        assertNull(WheelZonePalette.zoneForFolder("ambience/forest"))
        assertNull(WheelZonePalette.zoneForFolder("music/happy/not-a-tier"))
        assertNull(WheelZonePalette.zoneForFolder("music/transitions"))
    }

    @Test
    fun `folder path round-trips through zoneForFolder`() {
        WheelZonePalette.zoneIds.forEach { id ->
            val zone = WheelZonePalette.zoneById(id)!!
            val path = WheelZonePalette.folderPath(zone)
            val match = WheelZonePalette.zoneForFolder("music/$path")!!
            assertEquals(id, match.zone.id, "round-trip failed for $id (path $path)")
        }
    }
}
