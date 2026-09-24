package com.example.dndsound.core.music

import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.WheelPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WheelMusicControllerTest {

    @Test
    fun `first target always switches`() {
        val decision = WheelMusicController.decide(null, WheelPoint(0.5f, 0.5f), MusicMode.EXPLORATION, MusicMode.EXPLORATION)
        assertTrue(decision is WheelMusicController.Decision.Switch)
    }

    @Test
    fun `target within keep radius keeps the track`() {
        val anchor = WheelPoint(0.7f, 0f)
        val target = WheelPoint(0.75f, 0.1f) // distance ~0.11
        val decision = WheelMusicController.decide(anchor, target, MusicMode.EXPLORATION, MusicMode.EXPLORATION)
        assertEquals(WheelMusicController.Decision.Keep, decision)
    }

    @Test
    fun `target beyond keep radius switches with new anchor`() {
        val anchor = WheelPoint(0.7f, 0f)
        val target = WheelPoint(-0.7f, 0f) // distance 1.4
        val decision = WheelMusicController.decide(anchor, target, MusicMode.EXPLORATION, MusicMode.EXPLORATION)
        assertEquals(WheelMusicController.Decision.Switch(WheelPoint(-0.7f, 0f)), decision)
    }

    @Test
    fun `boundary at exactly keep radius keeps`() {
        // Distance exactly 0.4 must keep; only strictly beyond switches.
        val anchor = WheelPoint(0.4f, 0f)
        val decision = WheelMusicController.decide(anchor, WheelPoint(0f, 0f), MusicMode.EXPLORATION, MusicMode.EXPLORATION)
        assertEquals(WheelMusicController.Decision.Keep, decision)
    }

    @Test
    fun `mode change forces switch even at the same point`() {
        val anchor = WheelPoint(0.5f, 0f)
        val decision = WheelMusicController.decide(anchor, anchor, MusicMode.BATTLE, MusicMode.EXPLORATION)
        assertTrue(decision is WheelMusicController.Decision.Switch)
    }

    @Test
    fun `calm center switches when leaving a far anchor`() {
        val anchor = WheelPoint(0.8f, 0.8f)
        val decision = WheelMusicController.decide(anchor, WheelPoint.CENTER, MusicMode.EXPLORATION, MusicMode.EXPLORATION)
        assertTrue(decision is WheelMusicController.Decision.Switch)
    }
}
