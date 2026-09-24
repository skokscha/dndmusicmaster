package com.example.dndsound.core.wheel

import com.example.dndsound.core.model.WheelPoint
import kotlin.math.pow

/** One anchor on the color ring: a sector center or a boundary transition. */
data class ColorAnchor(val angleDeg: Float, val color: Int)

/**
 * Color of the wheel gradient at a point (docs "Цвета и градиент"):
 *
 * 1. by angle — the two neighbouring anchors are interpolated in OKLab with a
 *    smoothstep, giving the sector/transition color for that direction;
 * 2. by radius — mix(r) = 0 at r <= 0.10, 0.55 at r = 0.60, 1.0 at r = 1.0;
 * 3. the final color is the OKLab blend neutral -> sector color by mix(r).
 *
 * So the center is a neutral gray, the INNER tier is a muted version of the
 * sector color and the OUTER tier is fully saturated; transition bands carry
 * their own bridge color.
 */
object WheelColor {

    /** Radius where the neutral mix starts. */
    private const val MIX_START = 0.10f
    /** Neutral mix fraction at the tier split radius. */
    private const val MIX_AT_SPLIT = 0.55f

    fun at(
        p: WheelPoint,
        anchors: List<ColorAnchor>,
        neutral: Int,
        tierSplitRadius: Float = 0.60f,
    ): Int {
        val theta = WheelZones.fold360(p.angleDeg)
        val sectorColor = colorForAngle(theta, anchors)
        val mix = mixForRadius(p.radius, tierSplitRadius)
        return mixOklab(neutral, sectorColor, mix)
    }

    /** Smoothstepped OKLab interpolation between the two neighbouring anchors. */
    fun colorForAngle(thetaDeg: Float, anchors: List<ColorAnchor>): Int {
        require(anchors.size >= 2) { "need at least two anchors" }
        val sorted = anchors.sortedBy { it.angleDeg }
        val theta = WheelZones.fold360(thetaDeg)
        var lower = sorted.last()
        var upper = sorted.first()
        var span = 360f - lower.angleDeg + upper.angleDeg
        var offset = WheelZones.fold360(theta - lower.angleDeg)
        for (i in sorted.indices) {
            val a = sorted[i]
            val b = sorted[(i + 1) % sorted.size]
            val segmentSpan = if (b.angleDeg > a.angleDeg) b.angleDeg - a.angleDeg else 360f - a.angleDeg + b.angleDeg
            // Forward distance from a to theta, always in [0, 360).
            val forward = if (theta >= a.angleDeg) theta - a.angleDeg else theta + 360f - a.angleDeg
            if (forward <= segmentSpan + 1e-4f) {
                lower = a
                upper = b
                span = segmentSpan
                offset = forward
                break
            }
        }
        val t = smoothstep((offset / span).coerceIn(0f, 1f))
        return mixOklab(lower.color, upper.color, t)
    }

    /** Neutral mix fraction for a radius (0 in the center, 1 at the rim). */
    fun mixForRadius(r: Float, tierSplitRadius: Float = 0.60f): Float = when {
        r <= MIX_START -> 0f
        r <= tierSplitRadius -> MIX_AT_SPLIT * (r - MIX_START) / (tierSplitRadius - MIX_START)
        else -> MIX_AT_SPLIT + (1f - MIX_AT_SPLIT) * (r - tierSplitRadius) / (1f - tierSplitRadius)
    }

    private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)

    // ---------------------------------------------------------------- OKLab

    /** OKLab blend of two sRGB colors; t = 0 returns a, t = 1 returns b. */
    fun mixOklab(a: Int, b: Int, t: Float): Int {
        if (t <= 0f) return a
        if (t >= 1f) return b
        val la = linearSrgb(a)
        val lb = linearSrgb(b)
        val labA = linearToOklab(la)
        val labB = linearToOklab(lb)
        val mixed = Oklab(
            L = labA.L + (labB.L - labA.L) * t,
            aAxis = labA.aAxis + (labB.aAxis - labA.aAxis) * t,
            bAxis = labA.bAxis + (labB.bAxis - labA.bAxis) * t,
        )
        return srgbFromLinear(oklabToLinear(mixed))
    }

    private data class LinearRgb(val r: Double, val g: Double, val b: Double)
    private data class Oklab(val L: Double, val aAxis: Double, val bAxis: Double)

    private fun linearSrgb(color: Int) = LinearRgb(
        r = srgbToLinear(((color shr 16) and 0xFF) / 255.0),
        g = srgbToLinear(((color shr 8) and 0xFF) / 255.0),
        b = srgbToLinear((color and 0xFF) / 255.0),
    )

    private fun srgbToLinear(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun linearToSrgbByte(c: Double): Int {
        val srgb = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1.0 / 2.4) - 0.055
        return (srgb.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt()
    }

    private fun srgbFromLinear(rgb: LinearRgb): Int =
        (0xFF shl 24) or
            (linearToSrgbByte(rgb.r) shl 16) or
            (linearToSrgbByte(rgb.g) shl 8) or
            linearToSrgbByte(rgb.b)

    // Reference matrices: Bjorn Ottosson, "A perceptual color space for image processing".

    private fun linearToOklab(rgb: LinearRgb): Oklab {
        val l = 0.4122214708 * rgb.r + 0.5363325363 * rgb.g + 0.0514459929 * rgb.b
        val m = 0.2119034982 * rgb.r + 0.6806995451 * rgb.g + 0.1073969566 * rgb.b
        val s = 0.0883024619 * rgb.r + 0.2817188376 * rgb.g + 0.6299787005 * rgb.b
        val lPrime = cbrt(l)
        val mPrime = cbrt(m)
        val sPrime = cbrt(s)
        return Oklab(
            L = 0.2104542553 * lPrime + 0.7936177850 * mPrime - 0.0040720468 * sPrime,
            aAxis = 1.9779984951 * lPrime - 2.4285922050 * mPrime + 0.4505937099 * sPrime,
            bAxis = 0.0259040371 * lPrime + 0.7827717662 * mPrime - 0.8086757660 * sPrime,
        )
    }

    private fun oklabToLinear(lab: Oklab): LinearRgb {
        val lPrime = lab.L + 0.3963377774 * lab.aAxis + 0.2158037573 * lab.bAxis
        val mPrime = lab.L - 0.1055613458 * lab.aAxis - 0.0638541728 * lab.bAxis
        val sPrime = lab.L - 0.0894841775 * lab.aAxis - 1.2914855480 * lab.bAxis
        val l = lPrime * lPrime * lPrime
        val m = mPrime * mPrime * mPrime
        val s = sPrime * sPrime * sPrime
        return LinearRgb(
            r = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            b = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
    }

    private fun cbrt(x: Double): Double = when {
        x >= 0 -> x.pow(1.0 / 3.0)
        else -> -(-x).pow(1.0 / 3.0)
    }
}
