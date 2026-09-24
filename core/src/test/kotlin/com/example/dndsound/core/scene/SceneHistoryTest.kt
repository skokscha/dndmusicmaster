package com.example.dndsound.core.scene

import com.example.dndsound.core.model.Bus
import com.example.dndsound.core.model.BusGain
import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.SceneSnapshot
import com.example.dndsound.core.model.Weather
import com.example.dndsound.core.model.WheelPoint
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SceneHistoryTest {

    private fun snapshot(x: Float) = SceneSnapshot(musicPoint = WheelPoint(x, 0f))

    @Test
    fun `undo returns the previous pushed state`() {
        val history = SceneHistory()
        history.push(snapshot(0.1f))
        history.push(snapshot(0.5f))
        assertTrue(history.canUndo())

        assertEquals(0.5f, history.undo()!!.musicPoint.x)
        assertEquals(0.1f, history.undo()!!.musicPoint.x)
        assertFalse(history.canUndo())
        assertNull(history.undo())
    }

    @Test
    fun `pushing an identical consecutive snapshot is a no-op`() {
        val history = SceneHistory()
        history.push(snapshot(0.3f))
        history.push(snapshot(0.3f))
        assertEquals(1, history.size)
    }

    @Test
    fun `history is capped at the maximum size`() {
        val history = SceneHistory(maxSize = 3)
        for (i in 1..10) history.push(snapshot(i.toFloat()))
        assertEquals(3, history.size)
        // The three most recent snapshots survive: 10, 9, 8.
        assertEquals(10f, history.undo()!!.musicPoint.x)
        assertEquals(9f, history.undo()!!.musicPoint.x)
        assertEquals(8f, history.undo()!!.musicPoint.x)
        assertNull(history.undo())
    }

    @Test
    fun `clear empties the stack`() {
        val history = SceneHistory()
        history.push(snapshot(1f))
        history.clear()
        assertFalse(history.canUndo())
    }
}

class SceneSnapshotSerializationTest {

    @Test
    fun `snapshot json round-trip keeps all fields`() {
        val original = SceneSnapshot(
            musicPoint = WheelPoint(0.5f, -0.5f),
            musicMode = MusicMode.BATTLE,
            environmentId = "forest",
            enabledLayerIds = setOf("stream", "campfire"),
            layerGainsDb = mapOf("stream" to -4.5f),
            weather = Weather.RAIN,
            weatherIntensity = 0.6f,
            busGains = listOf(BusGain(Bus.MASTER, -1f), BusGain(Bus.SFX, -2f)),
        )

        val json = Json.encodeToString(original)
        val restored = Json.decodeFromString<SceneSnapshot>(json)

        assertEquals(original, restored)
    }

    @Test
    fun `snapshot restores bus gain lookup`() {
        val original = SceneSnapshot(
            busGains = listOf(BusGain(Bus.MASTER, -1f), BusGain(Bus.SFX, -2f)),
        )
        assertEquals(-1f, original.busGain(Bus.MASTER))
        assertEquals(-2f, original.busGain(Bus.SFX))
        assertEquals(0f, original.busGain(Bus.MUSIC))
    }
}
