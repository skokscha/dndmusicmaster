package com.example.dndsound.core.library

import com.example.dndsound.core.model.EnvironmentCategory
import com.example.dndsound.core.model.Mood
import com.example.dndsound.core.model.SoundCategory
import com.example.dndsound.core.model.WheelPoint
import com.example.dndsound.core.wheel.WheelMath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryRulesTest {

    @Test
    fun `audio extension detection is case-insensitive`() {
        assertTrue(LibraryRules.isAudioFile("song.OGG"))
        assertTrue(LibraryRules.isAudioFile("base.opus"))
        assertFalse(LibraryRules.isAudioFile("cover.jpg"))
        assertFalse(LibraryRules.isAudioFile("notes.txt"))
    }

    @Test
    fun `base name strips variant suffix`() {
        assertEquals("goblin", LibraryRules.baseName("goblin_01.ogg"))
        assertEquals("goblin", LibraryRules.baseName("goblin.ogg"))
        assertEquals("sword_hit", LibraryRules.baseName("sword_hit_12.mp3"))
        // Larger numbers are still variants; smaller numbers below 1000 too.
        assertEquals("rain", LibraryRules.baseName("rain_007.flac"))
    }

    @Test
    fun `display title replaces underscores with spaces`() {
        assertEquals("goblin", LibraryRules.displayTitle("goblin_01.ogg"))
        assertEquals("sword hit", LibraryRules.displayTitle("sword_hit_02.ogg"))
        assertEquals("wolf howl", LibraryRules.displayTitle("wolf_howl.wav"))
    }

    @Test
    fun `variants group by base name in numeric order`() {
        val grouped = LibraryRules.groupVariants(
            listOf(
                "goblin_02.ogg",
                "goblin_01.ogg",
                "goblin_10.ogg",
                "cover.jpg",
                "troll_01.ogg",
            ),
        )
        assertEquals(listOf("goblin_01.ogg", "goblin_02.ogg", "goblin_10.ogg"), grouped["goblin"])
        assertEquals(listOf("troll_01.ogg"), grouped["troll"])
        assertEquals(2, grouped.size)
    }

    @Test
    fun `mood folders map to wheel points`() {
        val happy = LibraryRules.defaultWheelPoint("happy")!!
        assertEquals(Mood.HAPPY, WheelMath.nearestMood(happy.angleDeg))
        assertEquals(0.7f, happy.radius, 1e-4f)

        val calm = LibraryRules.defaultWheelPoint("calm")!!
        assertTrue(calm.radius <= WheelMath.CALM_RADIUS)
        assertTrue(calm.isCalm())

        assertNull(LibraryRules.defaultWheelPoint("battle"))
        assertNull(LibraryRules.defaultWheelPoint("unknown_stuff"))
    }

    @Test
    fun `category folders resolve and unknown becomes custom`() {
        assertEquals(EnvironmentCategory.NATURE, LibraryRules.environmentCategory("nature"))
        assertEquals(EnvironmentCategory.CUSTOM, LibraryRules.environmentCategory("my_world"))
        assertEquals(SoundCategory.CREATURE, LibraryRules.soundCategory("creature"))
        assertEquals(SoundCategory.CUSTOM, LibraryRules.soundCategory("weird"))
    }

    @Test
    fun `wheel point geometry is consistent`() {
        val p = WheelPoint(0.6f, 0.8f)
        assertEquals(1f, p.radius, 1e-6f)
        assertEquals(53.13f, p.angleDeg, 1e-2f)

        val clamped = WheelPoint(3f, 4f).clamped()
        assertEquals(0.6f, clamped.x, 1e-6f)
        assertEquals(0.8f, clamped.y, 1e-6f)
    }
}
