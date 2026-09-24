package com.example.dndsound.core.oneshot

import com.example.dndsound.core.model.OneShot
import com.example.dndsound.core.model.RandomSpec
import com.example.dndsound.core.model.SoundFile
import com.example.dndsound.core.mixer.GainMath
import com.example.dndsound.core.mixer.PanMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class OneShotEngineTest {

    private class FakeOneShotMixer : OneShotMixer {
        data class PlayCall(val uri: String, val left: Float, val right: Float, val rate: Float)

        val plays = mutableListOf<PlayCall>()
        var stopAllCount = 0
        var released = false
        private var nextId = 1

        override fun play(uri: String, left: Float, right: Float, rate: Float): Int {
            plays += PlayCall(uri, left, right, rate)
            return nextId++
        }

        override fun stopAll() {
            stopAllCount++
        }

        override fun release() {
            released = true
        }
    }

    private class Harness {
        val mixer = FakeOneShotMixer()
        val duckCalls = mutableListOf<Float?>()

        fun engine(scope: CoroutineScope, clock: () -> Long, seed: Long = 1) = OneShotEngine(
            scope = scope,
            mixer = mixer,
            onDuck = { duckCalls += it },
            clockMs = clock,
            random = Random(seed),
        )
    }

    private fun shot(
        id: String,
        vararg uris: String,
        gainDb: Float = 0f,
        durationMs: Long = 5_000,
        random: RandomSpec? = null,
    ) = OneShot(
        id = id,
        name = id,
        variants = uris.map {
            SoundFile(id = it, title = it, uri = "content://$it", durationMs = durationMs)
        },
        gainDb = gainDb,
        random = random,
    )

    private fun dbToLinear(db: Float): Float = 10.0.pow(db / 20.0).toFloat()

    @Test
    fun `play picks variants without immediate repeats`() = runTest {
        val h = Harness()
        val engine = h.engine(backgroundScope, { testScheduler.currentTime })
        engine.setGroups(listOf(shot("g", "a", "b", "c")))
        runCurrent()

        repeat(10) {
            engine.play("g")
            runCurrent()
        }

        assertEquals(10, h.mixer.plays.size)
        val uris = h.mixer.plays.map { it.uri }
        assertTrue(uris.all { it in setOf("content://a", "content://b", "content://c") })
        uris.zipWithNext().forEach { (prev, next) ->
            assertNotEquals(prev, next, "repeated variant: $prev then $next")
        }
    }

    @Test
    fun `jitter stays within the spec and keeps constant power`() = runTest {
        val h = Harness()
        val engine = h.engine(backgroundScope, { testScheduler.currentTime })
        engine.setGroups(
            listOf(
                shot(
                    "g",
                    "a",
                    gainDb = -2f,
                    random = RandomSpec(
                        minIntervalSec = 0f,
                        maxIntervalSec = 0f,
                        gainJitterDb = 6f,
                        panJitter = 0.5f,
                        pitchJitterPct = 5f,
                    ),
                ),
            ),
        )
        runCurrent()

        repeat(50) {
            engine.play("g")
            runCurrent()
        }

        assertEquals(50, h.mixer.plays.size)
        val minGain = dbToLinear(-8f)
        val maxGain = dbToLinear(4f)
        h.mixer.plays.forEach { call ->
            val power = call.left * call.left + call.right * call.right
            assertTrue(power >= minGain * minGain - 1e-4f, "power too low: $power")
            assertTrue(power <= maxGain * maxGain + 1e-4f, "power too high: $power")
            assertTrue(call.left >= 0f && call.right >= 0f, "negative channel volume")
            assertTrue(call.rate in 0.95f..1.05f, "rate out of bounds: ${call.rate}")
        }
    }

    @Test
    fun `without a spec the sound plays exactly as indexed`() = runTest {
        val h = Harness()
        val engine = h.engine(backgroundScope, { testScheduler.currentTime })
        engine.setGroups(listOf(shot("g", "a", gainDb = -3f)))
        runCurrent()

        engine.play("g")
        runCurrent()

        val gain = dbToLinear(-3f)
        val (centerLeft, centerRight) = PanMath.volumes(0f)
        val call = h.mixer.plays.single()
        assertEquals("content://a", call.uri)
        assertEquals(centerLeft * gain, call.left, 1e-4f)
        assertEquals(centerRight * gain, call.right, 1e-4f)
        assertEquals(1f, call.rate, 1e-6f)
    }

    @Test
    fun `master and sfx bus gains scale the played volumes`() = runTest {
        val h = Harness()
        val engine = h.engine(backgroundScope, { testScheduler.currentTime })
        engine.setGroups(listOf(shot("g", "a")))
        engine.setBusGains(masterDb = -6f, sfxDb = -3f)
        runCurrent()

        engine.play("g")
        runCurrent()

        val gain = dbToLinear(-9f)
        val (centerLeft, centerRight) = PanMath.volumes(0f)
        val call = h.mixer.plays.single()
        assertEquals(centerLeft * gain, call.left, 1e-4f)
        assertEquals(centerRight * gain, call.right, 1e-4f)
    }

    @Test
    fun `first play ducks and the duck releases after the sound ends`() = runTest {
        val h = Harness()
        val engine = h.engine(backgroundScope, { testScheduler.currentTime })
        engine.setGroups(listOf(shot("g", "a", durationMs = 5_000)))
        runCurrent()

        engine.play("g")
        runCurrent()

        assertEquals(listOf(-3.5f), h.duckCalls)
        assertTrue(engine.state.value.ducking)
        assertEquals(1, engine.state.value.activeStreams)

        advanceTimeBy(5_000 + 300 + 1) // duration + tail
        runCurrent()

        assertEquals(listOf(-3.5f, null), h.duckCalls)
        assertFalse(engine.state.value.ducking)
        assertEquals(0, engine.state.value.activeStreams)
    }

    @Test
    fun `ducking disabled leaves the buses alone`() = runTest {
        val h = Harness()
        val engine = h.engine(backgroundScope, { testScheduler.currentTime })
        engine.setGroups(listOf(shot("g", "a")))
        engine.setDucking(enabled = false, db = -3.5f)
        runCurrent()

        engine.play("g")
        runCurrent()

        assertEquals(emptyList<Float?>(), h.duckCalls)
        assertFalse(engine.state.value.ducking)
        assertEquals(1, engine.state.value.activeStreams)
    }

    @Test
    fun `a second play postpones the unduck`() = runTest {
        val h = Harness()
        val engine = h.engine(backgroundScope, { testScheduler.currentTime })
        engine.setGroups(listOf(shot("g", "a", durationMs = 3_000)))
        runCurrent()

        engine.play("g")
        runCurrent()
        advanceTimeBy(100)
        engine.play("g")
        runCurrent()

        advanceTimeBy(3_250) // t = 3350: past the first deadline, before the second
        runCurrent()
        assertEquals(listOf(-3.5f), h.duckCalls)
        assertTrue(engine.state.value.ducking)

        advanceTimeBy(51) // t = 3401: past the second deadline (100 + 3300)
        runCurrent()

        assertEquals(listOf(-3.5f, null), h.duckCalls)
        assertFalse(engine.state.value.ducking)
    }

    @Test
    fun `stop all stops the mixer and releases the duck`() = runTest {
        val h = Harness()
        val engine = h.engine(backgroundScope, { testScheduler.currentTime })
        engine.setGroups(listOf(shot("g", "a")))
        runCurrent()

        engine.play("g")
        runCurrent()
        engine.stopAll()
        runCurrent()

        assertEquals(1, h.mixer.stopAllCount)
        assertEquals(0, engine.state.value.activeStreams)
        assertEquals(listOf(-3.5f, null), h.duckCalls)
    }

    @Test
    fun `unknown group is ignored`() = runTest {
        val h = Harness()
        val engine = h.engine(backgroundScope, { testScheduler.currentTime })
        engine.setGroups(listOf(shot("g", "a")))
        runCurrent()

        engine.play("nope")
        runCurrent()

        assertEquals(0, h.mixer.plays.size)
        assertEquals(emptyList<Float?>(), h.duckCalls)
    }

    @Test
    fun `set groups updates the state`() = runTest {
        val h = Harness()
        val engine = h.engine(backgroundScope, { testScheduler.currentTime })

        engine.setGroups(listOf(shot("g1", "a"), shot("g2", "b")))
        runCurrent()

        assertEquals(listOf("g1", "g2"), engine.state.value.groups.map { it.id })
    }

    @Test
    fun `release stops everything and frees the mixer`() = runTest {
        val h = Harness()
        val engine = h.engine(backgroundScope, { testScheduler.currentTime })
        engine.setGroups(listOf(shot("g", "a")))
        runCurrent()

        engine.play("g")
        runCurrent()
        engine.release()
        runCurrent()

        assertTrue(h.mixer.released)
        assertEquals(1, h.mixer.stopAllCount)
        assertEquals(listOf(-3.5f, null), h.duckCalls)
    }
}
