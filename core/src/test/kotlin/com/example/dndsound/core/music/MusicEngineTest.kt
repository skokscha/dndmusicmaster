package com.example.dndsound.core.music

import com.example.dndsound.core.audio.FakePlayerHandle
import com.example.dndsound.core.audio.PlayerEvent
import com.example.dndsound.core.model.Mood
import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.Track
import com.example.dndsound.core.model.WheelPoint
import com.example.dndsound.core.wheel.Selection
import com.example.dndsound.core.wheel.Tier
import com.example.dndsound.core.wheel.WheelZone
import com.example.dndsound.core.wheel.WheelZones
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalCoroutinesApi::class)
class MusicEngineTest {

    /** A battle-free exploration point on the sad sector's outer tier. */
    private val sadPoint = polar(0.8f, 0f) // angle 0 = sad
    private val epicPoint = polar(0.8f, 45f)

    private fun track(id: String, gainDb: Float = 0f, mode: MusicMode = MusicMode.EXPLORATION) = Track(
        id = id,
        title = id,
        uri = "content://$id",
        durationMs = 60_000,
        mode = mode,
        position = sadPoint,
        gainDb = gainDb,
    )

    private fun polar(radius: Float, angleDeg: Float) = WheelPoint(
        x = radius * cos(Math.toRadians(angleDeg.toDouble())).toFloat(),
        y = radius * sin(Math.toRadians(angleDeg.toDouble())).toFloat(),
    )

    private class Harness {
        val handles = mutableListOf<FakePlayerHandle>()
        val selectCalls = mutableListOf<SelectCall>()
        val selectQueue = ArrayDeque<Selection?>()
        val nextQueue = ArrayDeque<Selection?>()
        val nextCalls = mutableListOf<List<String>>()

        data class SelectCall(val target: WheelZone, val point: WheelPoint, val mode: MusicMode, val recent: List<String>)
    }

    private fun newEngine(
        harness: Harness,
        scope: CoroutineScope,
        crossfadeMs: Long = 100,
    ): MusicEngine = MusicEngine(
        scope = scope,
        playerFactory = { FakePlayerHandle().also(harness.handles::add) },
        selectTrack = { target, point, mode, recent ->
            harness.selectCalls += Harness.SelectCall(target, point, mode, recent)
            harness.selectQueue.removeFirstOrNull()
        },
        selectNextTrack = { _, recent ->
            harness.nextCalls += recent
            harness.nextQueue.removeFirstOrNull()
        },
        crossfadeMs = crossfadeMs,
        rampStepMs = 25,
    )

    private fun selection(id: String, zone: WheelZone? = null, fallback: Boolean = false) =
        Selection(track(id), zone ?: WheelZones.zoneAt(sadPoint), fallback)
    @Test
    fun `first wheel target fades the track in`() = runTest {
        val harness = Harness()
        harness.selectQueue += selection("t1")
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(sadPoint)
        advanceTimeBy(601 + 200) // debounce + fade
        runCurrent()

        assertEquals("t1", engine.state.value.currentTrack?.id)
        assertTrue(engine.state.value.playing)
        assertEquals(listOf("content://t1"), harness.handles[0].sources)
        assertFalse(harness.handles[0].paused)
        assertEquals(1f, harness.handles[0].volume, 1e-3f)
        assertEquals(WheelZones.zoneAt(sadPoint), engine.state.value.playingZone)
        assertFalse(engine.state.value.isFallback)
    }

    @Test
    fun `marker moves inside a zone keep the current track`() = runTest {
        val harness = Harness()
        harness.selectQueue += selection("t1")
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(sadPoint)
        advanceTimeBy(801)
        runCurrent()
        assertEquals(1, harness.selectCalls.size)

        // Same sad-outer zone, ~0.11 away: no re-selection, marker follows.
        engine.setWheelTarget(WheelPoint(0.75f, 0.1f))
        advanceTimeBy(801)
        runCurrent()

        assertEquals(1, harness.selectCalls.size)
        assertEquals("t1", engine.state.value.currentTrack?.id)
        assertEquals(WheelPoint(0.75f, 0.1f), engine.state.value.anchor)
    }

    @Test
    fun `zone change crossfades to a new track`() = runTest {
        val harness = Harness()
        harness.selectQueue += selection("t1")
        harness.selectQueue += selection("t2", WheelZone.Sector(Mood.CREEPY, Tier.OUTER))
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(sadPoint)
        advanceTimeBy(801)
        runCurrent()

        engine.setWheelTarget(WheelPoint(-0.7f, 0f)) // creepy outer tier
        advanceTimeBy(801)
        runCurrent()

        assertEquals("t2", engine.state.value.currentTrack?.id)
        assertEquals(listOf("content://t2"), harness.handles[1].sources)
        assertEquals(0f, harness.handles[0].volume, 1e-3f)
        assertTrue(harness.handles[0].paused)
        assertEquals(1f, harness.handles[1].volume, 1e-3f)
        assertFalse(engine.state.value.crossfading)
        assertEquals(WheelZone.Sector(Mood.CREEPY, Tier.OUTER), engine.state.value.playingZone)
    }

    @Test
    fun `boundary jitter within hysteresis does not re-select`() = runTest {
        val harness = Harness()
        harness.selectQueue += selection("t1")
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(epicPoint) // epic outer tier
        advanceTimeBy(801)
        runCurrent()
        assertEquals(1, harness.selectCalls.size)

        // 0.5 degrees beyond the clear-zone edge: inside the hysteresis band.
        engine.setWheelTarget(polar(0.8f, 28f))
        advanceTimeBy(801)
        runCurrent()

        assertEquals(1, harness.selectCalls.size, "hysteresis must keep the zone stable")
        assertEquals("t1", engine.state.value.currentTrack?.id)
    }

    @Test
    fun `crossing the hysteresis band re-selects for the new zone`() = runTest {
        val harness = Harness()
        harness.selectQueue += selection("t1")
        harness.selectQueue += selection("t2", WheelZone.Transition("transition.tragic_fight", Mood.EPIC, Mood.SAD))
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(epicPoint)
        advanceTimeBy(801)
        runCurrent()

        // 2.5 degrees beyond the clear-zone edge: a real zone change.
        engine.setWheelTarget(polar(0.8f, 26f))
        advanceTimeBy(801)
        runCurrent()

        assertEquals(2, harness.selectCalls.size)
        assertEquals("transition.tragic_fight", harness.selectCalls[1].target.id)
        assertEquals("t2", engine.state.value.currentTrack?.id)
        assertTrue(engine.state.value.targetZone is WheelZone.Transition)
    }

    @Test
    fun `recent ids are passed to the selector`() = runTest {
        val harness = Harness()
        harness.selectQueue += selection("t1")
        harness.selectQueue += selection("t2")
        val engine = newEngine(harness, backgroundScope)

        harness.nextQueue += selection("t2")
        engine.setWheelTarget(sadPoint)
        advanceTimeBy(801)
        runCurrent()
        engine.next()
        advanceTimeBy(201)
        runCurrent()

        assertEquals(emptyList<String>(), harness.selectCalls[0].recent)
        assertEquals(listOf("t1"), harness.nextCalls.single())
    }

    @Test
    fun `track end auto advances with a crossfade`() = runTest {
        val harness = Harness()
        harness.selectQueue += selection("t1")
        harness.selectQueue += selection("t2")
        val engine = newEngine(harness, backgroundScope)

        harness.nextQueue += selection("t2")
        engine.setWheelTarget(sadPoint)
        advanceTimeBy(801)
        runCurrent()
        assertEquals("t1", engine.state.value.currentTrack?.id)

        harness.handles[0].emit(PlayerEvent.Ended)
        advanceTimeBy(201)
        runCurrent()

        assertEquals("t2", engine.state.value.currentTrack?.id)
        assertEquals(listOf("content://t2"), harness.handles[1].sources)
    }

    @Test
    fun `mode switch forces a battle selection`() = runTest {
        val harness = Harness()
        harness.selectQueue += selection("t1")
        harness.selectQueue += Selection(track("b1", mode = MusicMode.BATTLE), WheelZone.Neutral, isFallback = false)
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(sadPoint)
        advanceTimeBy(801)
        runCurrent()

        engine.setMode(MusicMode.BATTLE)
        advanceTimeBy(201)
        runCurrent()

        assertEquals(MusicMode.EXPLORATION, harness.selectCalls[0].mode)
        assertEquals(MusicMode.BATTLE, harness.selectCalls[1].mode)
        assertEquals("b1", engine.state.value.currentTrack?.id)
        assertEquals(MusicMode.BATTLE, engine.state.value.mode)
    }

    @Test
    fun `selector returning null stops playback and clears the playing zone`() = runTest {
        val harness = Harness()
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(sadPoint)
        advanceTimeBy(801)
        runCurrent()

        assertFalse(engine.state.value.playing)
        assertNull(engine.state.value.currentTrack)
        assertNull(engine.state.value.playingZone)
        assertTrue(harness.handles[0].paused)
        assertEquals(0f, harness.handles[0].volume, 1e-3f)
    }

    @Test
    fun `pause and resume ramp volume back`() = runTest {
        val harness = Harness()
        harness.selectQueue += selection("t1")
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(sadPoint)
        advanceTimeBy(801)
        runCurrent()

        engine.pause()
        runCurrent()
        assertTrue(harness.handles[0].paused)
        assertFalse(engine.state.value.playing)

        engine.resume()
        advanceTimeBy(301)
        runCurrent()
        assertFalse(harness.handles[0].paused)
        assertTrue(engine.state.value.playing)
        assertEquals(1f, harness.handles[0].volume, 1e-3f)
    }

    @Test
    fun `bus gains scale the final volume`() = runTest {
        val harness = Harness()
        harness.selectQueue += selection("t1")
        val engine = newEngine(harness, backgroundScope)

        engine.setBusGains(masterDb = 0f, musicBusDb = -6f)
        runCurrent()
        engine.setWheelTarget(sadPoint)
        advanceTimeBy(801)
        runCurrent()

        val expected = Math.pow(10.0, -6.0 / 20.0).toFloat()
        assertEquals(expected, harness.handles[0].volume, 0.01f)
    }

    @Test
    fun `duck scales the playing volume and restore ramps back`() = runTest {
        val harness = Harness()
        harness.selectQueue += selection("t1")
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(sadPoint)
        advanceTimeBy(801)
        runCurrent()
        assertEquals(1f, harness.handles[0].volume, 1e-3f)

        engine.setDuck(-3.5f)
        advanceTimeBy(201)
        runCurrent()
        val ducked = Math.pow(10.0, -3.5 / 20.0).toFloat()
        assertEquals(ducked, harness.handles[0].volume, 0.01f)

        engine.setDuck(null)
        advanceTimeBy(201)
        runCurrent()
        assertEquals(1f, harness.handles[0].volume, 1e-3f)
    }

    @Test
    fun `playback error is surfaced in state`() = runTest {
        val harness = Harness()
        harness.selectQueue += selection("t1")
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(sadPoint)
        advanceTimeBy(801)
        runCurrent()

        harness.handles[0].emit(PlayerEvent.Error("source broken"))
        runCurrent()

        assertEquals("source broken", engine.state.value.error)
    }

    @Test
    fun `release stops both players`() = runTest {
        val harness = Harness()
        harness.selectQueue += selection("t1")
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(sadPoint)
        advanceTimeBy(801)
        runCurrent()

        engine.release()
        runCurrent()

        assertTrue(harness.handles.all { it.released })
        assertFalse(engine.state.value.playing)
        assertNull(engine.state.value.currentTrack)
    }
}
