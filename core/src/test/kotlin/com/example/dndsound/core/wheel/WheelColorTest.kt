package com.example.dndsound.core.wheel

import com.example.dndsound.core.model.WheelPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class WheelColorTest {

    private val anchors = WheelZonePalette.colorAnchors()
    private val neutral = WheelZonePalette.neutralColor

    private fun pointAt(angleDeg: Float, radius: Float) = WheelPoint(
        x = radius * cos(Math.toRadians(angleDeg.toDouble())).toFloat(),
        y = radius * sin(Math.toRadians(angleDeg.toDouble())).toFloat(),
    )

    @Test
    fun `sixteen anchors cover sector centers and boundaries`() {
        assertEquals(16, anchors.size)
        assertEquals(16, anchors.map { it.angleDeg }.toSet().size)
    }

    @Test
    fun `rim color at a sector center is the exact palette color`() {
        assertEquals(WheelZonePalette.sectorColor(com.example.dndsound.core.model.Mood.HAPPY), WheelColor.at(pointAt(90f, 1f), anchors, neutral))
        assertEquals(WheelZonePalette.sectorColor(com.example.dndsound.core.model.Mood.EPIC), WheelColor.at(pointAt(45f, 1f), anchors, neutral))
        assertEquals(WheelZonePalette.sectorColor(com.example.dndsound.core.model.Mood.MAGICAL), WheelColor.at(pointAt(180f, 1f), anchors, neutral))
    }

    @Test
    fun `rim color at a boundary is the exact transition color`() {
        assertEquals(WheelZonePalette.transitionColor(happy, epic), WheelColor.at(pointAt(67.5f, 1f), anchors, neutral))
        assertEquals(WheelZonePalette.transitionColor(epic, sad), WheelColor.at(pointAt(22.5f, 1f), anchors, neutral))
    }

    @Test
    fun `center is the pure neutral color`() {
        assertEquals(neutral, WheelColor.at(WheelPoint.CENTER, anchors, neutral))
        assertEquals(neutral, WheelColor.at(pointAt(123f, 0.05f), anchors, neutral))
    }

    @Test
    fun `mix grows monotonically with radius`() {
        var previous = 0f
        listOf(0.05f, 0.1f, 0.2f, 0.35f, 0.6f, 0.8f, 1f).forEach { r ->
            val mix = WheelColor.mixForRadius(r)
            assertTrue(mix >= previous - 1e-6f, "mix dropped at r=$r")
            previous = mix
        }
        assertEquals(0f, WheelColor.mixForRadius(0.1f), 1e-6f)
        assertEquals(0.55f, WheelColor.mixForRadius(0.6f), 1e-6f)
        assertEquals(1f, WheelColor.mixForRadius(1f), 1e-6f)
    }

    @Test
    fun `inner tier color sits between neutral and the rim color`() {
        val rim = WheelColor.at(pointAt(45f, 1f), anchors, neutral)
        val inner = WheelColor.at(pointAt(45f, 0.4f), anchors, neutral)
        val neutralDistance = channelDistance(inner, neutral)
        val rimDistance = channelDistance(inner, rim)
        assertTrue(
            neutralDistance < rimDistance,
            "inner tier must be closer to neutral than to the rim color",
        )
    }

    @Test
    fun `oklab mix endpoints and midpoint are sane`() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        assertEquals(white, WheelColor.mixOklab(white, black, 0f))
        assertEquals(black, WheelColor.mixOklab(white, black, 1f))
        val mid = WheelColor.mixOklab(white, black, 0.5f)
        // OKLab L = 0.5 is perceptual, so the sRGB midpoint is darker than 128.
        val midGray = (mid and 0xFF)
        assertTrue(abs(midGray - 99) <= 8, "unexpected mid gray: $mid")
        // Same color mixing with itself is stable.
        assertEquals(0x123456, WheelColor.mixOklab(0xFF123456.toInt(), 0xFF123456.toInt(), 0.5f) and 0xFFFFFF)
    }

    @Test
    fun `srgb round-trip through oklab keeps colors within a step`() {
        listOf(
            0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFF6C744.toInt(),
            0xFF6B1D45.toInt(), 0xFF2FB7C9.toInt(), 0xFF9CCB3A.toInt(),
        ).forEach { color ->
            val back = WheelColor.mixOklab(color, color, 0.999f)
            assertTrue(channelDistance(color, back) <= 2, "round-trip drifted for ${color.toUInt().toString(16)}")
        }
    }

    private fun channelDistance(a: Int, b: Int): Int {
        val dr = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
        val dg = abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
        val db = abs((a and 0xFF) - (b and 0xFF))
        return maxOf(dr, dg, db)
    }

    private companion object {
        val happy = com.example.dndsound.core.model.Mood.HAPPY
        val epic = com.example.dndsound.core.model.Mood.EPIC
        val sad = com.example.dndsound.core.model.Mood.SAD
    }
}
