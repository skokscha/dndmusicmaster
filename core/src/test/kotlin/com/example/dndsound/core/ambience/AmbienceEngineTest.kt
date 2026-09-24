package com.example.dndsound.core.ambience

import com.example.dndsound.core.audio.FakePlayerHandle
import com.example.dndsound.core.audio.PlayerEvent
import com.example.dndsound.core.model.AmbienceLayer
import com.example.dndsound.core.model.Environment
import com.example.dndsound.core.model.LayerKind
import com.example.dndsound.core.model.RandomSpec
import com.example.dndsound.core.model.TimeOfDay
import com.example.dndsound.core.model.Weather
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class AmbienceEngineTest {

    private companion object {
        // Player order: 0 base, 1 weather, 2..5 layer pool, 6..7 fallback looper.
        const val BASE = 0
        const val WEATHER = 1
        const val POOL0 = 2
    }

    private fun env(
        baseDay: String? = "content://day",
        baseNight: String? = "content://night",
        seamless: Boolean = true,
        baseDurationMs: Long = 0,
        layers: List<AmbienceLayer> = emptyList(),
    ) = Environment(
        id = "ambience/forest",
        name = "Forest",
        baseDayUri = baseDay,
        baseNightUri = baseNight,
        layers = layers,
        seamless = seamless,
        baseDurationMs = baseDurationMs,
    )

    private fun loopLayer(id: String, uri: String = "content://$id", defaultEnabled: Boolean = true) =
        AmbienceLayer(id = id, name = id, kind = LayerKind.LOOP, uris = listOf(uri), defaultEnabled = defaultEnabled)

    private fun spotLayer(id: String, vararg uris: String) = AmbienceLayer(
        id = id,
        name = id,
        kind = LayerKind.RANDOM,
        uris = uris.toList(),
        random = RandomSpec(minIntervalSec = 1f, maxIntervalSec = 1f),
        defaultEnabled = true,
    )

    private fun newEngine(
        scope: CoroutineScope,
        weather: List<String> = listOf("content://rain"),
        seed: Long = 42,
    ): Pair<AmbienceEngine, List<FakePlayerHandle>> {
        val handles = mutableListOf<FakePlayerHandle>()
        val engine = AmbienceEngine(
            scope = scope,
            playerFactory = { FakePlayerHandle().also(handles::add) },
            weatherFiles = { weather },
            crossfadeMs = 100,
            rampStepMs = 25,
            random = Random(seed),
        )
        return engine to handles
    }

    private fun dbToLinear(db: Float): Float = 10.0.pow(db / 20.0).toFloat()

    @Test
    fun `environment starts the day base looping and faded in`() = runTest {
        val (engine, h) = newEngine(backgroundScope)

        engine.setEnvironment(env())
        advanceTimeBy(201)
        runCurrent()

        assertEquals(listOf("content://day"), h[BASE].sources)
        assertTrue(h[BASE].looping)
        assertFalse(h[BASE].paused)
        assertEquals(1f, h[BASE].volume, 1e-3f)
        assertTrue(engine.state.value.playing)
        assertEquals("Forest", engine.state.value.environmentName)
    }

    @Test
    fun `time of day switch swaps the base source through silence`() = runTest {
        val (engine, h) = newEngine(backgroundScope)
        engine.setEnvironment(env())
        advanceTimeBy(600)
        runCurrent()

        engine.setTimeOfDay(TimeOfDay.NIGHT)
        advanceTimeBy(600)
        runCurrent()

        assertEquals("content://night", h[BASE].sources.last())
        assertEquals(TimeOfDay.NIGHT, engine.state.value.timeOfDay)
        assertEquals(1f, h[BASE].volume, 1e-3f)
    }

    @Test
    fun `loop layer occupies a pool slot and fades in`() = runTest {
        val (engine, h) = newEngine(backgroundScope)

        engine.setEnvironment(env(layers = listOf(loopLayer("stream", "content://stream"))))
        advanceTimeBy(600)
        runCurrent()

        assertEquals(listOf("content://stream"), h[POOL0].sources)
        assertTrue(h[POOL0].looping)
        assertEquals(1f, h[POOL0].volume, 1e-3f)
        val layer = engine.state.value.layers.single()
        assertTrue(layer.enabled)
        assertEquals(LayerKind.LOOP, layer.kind)
    }

    @Test
    fun `base and layer fade in together`() = runTest {
        // Regression: with one global fade generation the layer fade-in used
        // to supersede the base fade-in, leaving the base silent.
        val (engine, h) = newEngine(backgroundScope)
        engine.setEnvironment(env(layers = listOf(loopLayer("stream"))))
        advanceTimeBy(600)
        runCurrent()

        assertEquals(1f, h[BASE].volume, 1e-3f)
        assertEquals(1f, h[POOL0].volume, 1e-3f)
    }

    @Test
    fun `two loop layers get their own slots and both fade in`() = runTest {
        val (engine, h) = newEngine(backgroundScope)
        engine.setEnvironment(env(layers = listOf(loopLayer("stream"), loopLayer("campfire"))))
        advanceTimeBy(600)
        runCurrent()

        assertEquals(1f, h[POOL0].volume, 1e-3f)
        assertEquals(1f, h[POOL0 + 1].volume, 1e-3f)
        assertTrue(h[POOL0].looping)
        assertTrue(h[POOL0 + 1].looping)
        assertEquals(2, engine.state.value.layers.count { it.enabled })
    }

    @Test
    fun `duck scales base and weather and restore ramps back`() = runTest {
        val (engine, h) = newEngine(backgroundScope)
        engine.setEnvironment(env())
        engine.setWeather(Weather.RAIN, 0.5f)
        advanceTimeBy(600)
        runCurrent()

        engine.setDuck(-3.5f)
        advanceTimeBy(300)
        runCurrent()
        val ducked = dbToLinear(-3.5f)
        assertEquals(ducked, h[BASE].volume, 0.01f)
        assertEquals(dbToLinear(-12f) * ducked, h[WEATHER].volume, 0.01f)

        engine.setDuck(null)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(1f, h[BASE].volume, 1e-3f)
        assertEquals(dbToLinear(-12f), h[WEATHER].volume, 0.01f)
    }

    @Test
    fun `layer toggle off fades the slot out`() = runTest {
        val (engine, h) = newEngine(backgroundScope)
        engine.setEnvironment(env(layers = listOf(loopLayer("stream"))))
        advanceTimeBy(201)
        runCurrent()

        engine.setLayerEnabled("stream", false)
        advanceTimeBy(201)
        runCurrent()

        assertTrue(h[POOL0].paused)
        assertEquals(0f, h[POOL0].volume, 1e-3f)
        assertFalse(engine.state.value.layers.single().enabled)
    }

    @Test
    fun `layer gain change applies to the playing slot`() = runTest {
        val (engine, h) = newEngine(backgroundScope)
        engine.setEnvironment(env(layers = listOf(loopLayer("stream"))))
        advanceTimeBy(201)
        runCurrent()

        engine.setLayerGain("stream", -6f)
        advanceTimeBy(151)
        runCurrent()

        assertEquals(dbToLinear(-6f), h[POOL0].volume, 0.01f)
    }

    @Test
    fun `spots fire after the interval borrow a slot and alternate variants`() = runTest {
        val (engine, h) = newEngine(backgroundScope, seed = 7)
        engine.setEnvironment(env(layers = listOf(spotLayer("birds", "content://birds_01", "content://birds_02"))))
        runCurrent()

        advanceTimeBy(3000) // interval is 1..1 s; scheduling starts after setup
        runCurrent()
        val first = h[POOL0].sources.single()
        assertTrue(first == "content://birds_01" || first == "content://birds_02")
        assertTrue(h[POOL0].speed in 0.9f..1.1f)

        h[POOL0].emit(PlayerEvent.Ended) // spot finished, slot freed
        runCurrent()

        advanceTimeBy(1500)
        runCurrent()
        val second = h[POOL0].sources.last()
        assertTrue(second != first) // pickVariant never repeats with alternatives
    }

    @Test
    fun `weather crossfades and intensity scales the gain`() = runTest {
        val (engine, h) = newEngine(backgroundScope)
        engine.setEnvironment(env())
        engine.setWeather(Weather.RAIN, intensity = 0.5f)
        advanceTimeBy(600)
        runCurrent()

        assertEquals(listOf("content://rain"), h[WEATHER].sources)
        assertTrue(h[WEATHER].looping)
        assertEquals(dbToLinear(-12f), h[WEATHER].volume, 0.01f)

        engine.setWeather(Weather.NONE)
        advanceTimeBy(600)
        runCurrent()
        assertTrue(h[WEATHER].paused)
        assertEquals(0f, h[WEATHER].volume, 1e-3f)
        assertEquals(Weather.NONE, engine.state.value.weather)
    }

    @Test
    fun `weather without files stays silent`() = runTest {
        val (engine, h) = newEngine(backgroundScope, weather = emptyList())

        engine.setWeather(Weather.STORM, 1f)
        advanceTimeBy(201)
        runCurrent()

        assertEquals(0, h[WEATHER].sources.size)
        assertTrue(h[WEATHER].paused)
    }

    @Test
    fun `environment without base still plays layers`() = runTest {
        val (engine, h) = newEngine(backgroundScope)

        engine.setEnvironment(env(baseDay = null, baseNight = null, layers = listOf(loopLayer("campfire"))))
        advanceTimeBy(201)
        runCurrent()

        assertEquals(0, h[BASE].sources.size)
        assertEquals(1, h[POOL0].sources.size)
        assertTrue(engine.state.value.playing)
    }

    @Test
    fun `non seamless environment with known duration uses the fallback looper`() = runTest {
        val (engine, h) = newEngine(backgroundScope)

        engine.setEnvironment(env(seamless = false, baseDurationMs = 60_000))
        advanceTimeBy(201)
        runCurrent()

        assertEquals(0, h[BASE].sources.size) // regular base slot unused
        assertEquals(listOf("content://day"), h[6].sources)
        assertFalse(h[6].looping)
    }

    @Test
    fun `pause and resume stop and restore everything`() = runTest {
        val (engine, h) = newEngine(backgroundScope)
        engine.setEnvironment(env(layers = listOf(loopLayer("stream"))))
        engine.setWeather(Weather.RAIN, 0.5f)
        advanceTimeBy(201)
        runCurrent()

        engine.pause()
        runCurrent()
        assertFalse(engine.state.value.playing)
        assertTrue(h[BASE].paused)
        assertTrue(h[WEATHER].paused)
        assertTrue(h[POOL0].paused)

        engine.resume()
        advanceTimeBy(201)
        runCurrent()
        assertTrue(engine.state.value.playing)
        assertFalse(h[BASE].paused)
        assertFalse(h[WEATHER].paused)
        assertFalse(h[POOL0].paused)
    }

    @Test
    fun `clearing the environment fades everything out`() = runTest {
        val (engine, h) = newEngine(backgroundScope)
        engine.setEnvironment(env(layers = listOf(loopLayer("stream"))))
        advanceTimeBy(600)
        runCurrent()

        engine.setEnvironment(null)
        advanceTimeBy(600)
        runCurrent()

        assertFalse(engine.state.value.playing)
        assertTrue(h[BASE].paused)
        assertTrue(h[POOL0].paused)
        assertEquals(0f, h[BASE].volume, 1e-3f)
    }
}
