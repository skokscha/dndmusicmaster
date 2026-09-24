package com.example.dndsound.core.music

import com.example.dndsound.core.audio.FakePlayerHandle
import com.example.dndsound.core.audio.PlayerEvent
import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.Track
import com.example.dndsound.core.model.WheelPoint
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

@OptIn(ExperimentalCoroutinesApi::class)
class MusicEngineTest {

    private fun track(id: String, gainDb: Float = 0f, mode: MusicMode = MusicMode.EXPLORATION) = Track(
        id = id,
        title = id,
        uri = "content://$id",
        durationMs = 60_000,
        mode = mode,
        position = WheelPoint(0.7f, 0f),
        gainDb = gainDb,
    )

    private class Harness {
        val handles = mutableListOf<FakePlayerHandle>()
        val selections = mutableListOf<Pair<MusicMode, List<String>>>()
        val queue = ArrayDeque<Track>()
        val nextQueue = ArrayDeque<Track>()
        val nextCalls = mutableListOf<List<String>>()
    }

    private fun newEngine(
        harness: Harness,
        scope: CoroutineScope,
        crossfadeMs: Long = 100,
    ): MusicEngine = MusicEngine(
        scope = scope,
        playerFactory = { FakePlayerHandle().also(harness.handles::add) },
        selectTrack = { _, mode, recent ->
            harness.selections += mode to recent
            harness.queue.removeFirstOrNull()
        },
        selectNextTrack = { _, recent ->
            harness.nextCalls += recent
            harness.nextQueue.removeFirstOrNull()
        },
        crossfadeMs = crossfadeMs,
        rampStepMs = 25,
    )

    @Test
    fun `first wheel target fades the track in`() = runTest {
        val harness = Harness()
        harness.queue += track("t1")
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(WheelPoint(0.7f, 0f))
        advanceTimeBy(601 + 200) // debounce + fade
        runCurrent()

        assertEquals("t1", engine.state.value.currentTrack?.id)
        assertTrue(engine.state.value.playing)
        assertEquals(listOf("content://t1"), harness.handles[0].sources)
        assertFalse(harness.handles[0].paused)
        assertEquals(1f, harness.handles[0].volume, 1e-3f)
    }

    @Test
    fun `small wheel move keeps the current track`() = runTest {
        val harness = Harness()
        harness.queue += track("t1")
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(WheelPoint(0.7f, 0f))
        advanceTimeBy(801)
        runCurrent()
        assertEquals(1, harness.selections.size)

        engine.setWheelTarget(WheelPoint(0.75f, 0.1f)) // distance ~0.11 < KEEP_RADIUS
        advanceTimeBy(801)
        runCurrent()

        assertEquals(1, harness.selections.size)
        assertEquals("t1", engine.state.value.currentTrack?.id)
    }

    @Test
    fun `wheel move beyond keep radius crossfades to a new track`() = runTest {
        val harness = Harness()
        harness.queue += track("t1")
        harness.queue += track("t2")
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(WheelPoint(0.7f, 0f))
        advanceTimeBy(801)
        runCurrent()

        engine.setWheelTarget(WheelPoint(-0.7f, 0f)) // distance 1.4
        advanceTimeBy(801)
        runCurrent()

        assertEquals("t2", engine.state.value.currentTrack?.id)
        assertEquals(listOf("content://t2"), harness.handles[1].sources)
        assertEquals(0f, harness.handles[0].volume, 1e-3f)
        assertTrue(harness.handles[0].paused)
        assertEquals(1f, harness.handles[1].volume, 1e-3f)
        assertFalse(engine.state.value.crossfading)
    }

    @Test
    fun `recent ids are passed to the selector`() = runTest {
        val harness = Harness()
        harness.queue += track("t1")
        harness.queue += track("t2")
        val engine = newEngine(harness, backgroundScope)

        harness.nextQueue += track("t2")
        engine.setWheelTarget(WheelPoint(0.7f, 0f))
        advanceTimeBy(801)
        runCurrent()
        engine.next()
        advanceTimeBy(201)
        runCurrent()

        assertEquals(emptyList<String>(), harness.selections[0].second)
        assertEquals(listOf("t1"), harness.nextCalls.single())
    }

    @Test
    fun `track end auto advances with a crossfade`() = runTest {
        val harness = Harness()
        harness.queue += track("t1")
        harness.queue += track("t2")
        val engine = newEngine(harness, backgroundScope)

        harness.nextQueue += track("t2")
        engine.setWheelTarget(WheelPoint(0.7f, 0f))
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
        harness.queue += track("t1")
        harness.queue += track("b1", mode = MusicMode.BATTLE)
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(WheelPoint(0.7f, 0f))
        advanceTimeBy(801)
        runCurrent()

        engine.setMode(MusicMode.BATTLE)
        advanceTimeBy(201)
        runCurrent()

        assertEquals(MusicMode.EXPLORATION, harness.selections[0].first)
        assertEquals(MusicMode.BATTLE, harness.selections[1].first)
        assertEquals("b1", engine.state.value.currentTrack?.id)
        assertEquals(MusicMode.BATTLE, engine.state.value.mode)
    }

    @Test
    fun `selector returning null stops playback`() = runTest {
        val harness = Harness()
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(WheelPoint(0.7f, 0f))
        advanceTimeBy(801)
        runCurrent()

        assertFalse(engine.state.value.playing)
        assertNull(engine.state.value.currentTrack)
        assertTrue(harness.handles[0].paused)
        assertEquals(0f, harness.handles[0].volume, 1e-3f)
    }

    @Test
    fun `pause and resume ramp volume back`() = runTest {
        val harness = Harness()
        harness.queue += track("t1")
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(WheelPoint(0.7f, 0f))
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
        harness.queue += track("t1")
        val engine = newEngine(harness, backgroundScope)

        engine.setBusGains(masterDb = 0f, musicBusDb = -6f)
        runCurrent()
        engine.setWheelTarget(WheelPoint(0.7f, 0f))
        advanceTimeBy(801)
        runCurrent()

        val expected = Math.pow(10.0, -6.0 / 20.0).toFloat()
        assertEquals(expected, harness.handles[0].volume, 0.01f)
    }

    @Test
    fun `duck scales the playing volume and restore ramps back`() = runTest {
        val harness = Harness()
        harness.queue += track("t1")
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(WheelPoint(0.7f, 0f))
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
        harness.queue += track("t1")
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(WheelPoint(0.7f, 0f))
        advanceTimeBy(801)
        runCurrent()

        harness.handles[0].emit(PlayerEvent.Error("source broken"))
        runCurrent()

        assertEquals("source broken", engine.state.value.error)
    }

    @Test
    fun `release stops both players`() = runTest {
        val harness = Harness()
        harness.queue += track("t1")
        val engine = newEngine(harness, backgroundScope)

        engine.setWheelTarget(WheelPoint(0.7f, 0f))
        advanceTimeBy(801)
        runCurrent()

        engine.release()
        runCurrent()

        assertTrue(harness.handles.all { it.released })
        assertFalse(engine.state.value.playing)
        assertNull(engine.state.value.currentTrack)
    }
}
