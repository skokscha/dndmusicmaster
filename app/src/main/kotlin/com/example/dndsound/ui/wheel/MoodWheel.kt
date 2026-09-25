package com.example.dndsound.ui.wheel

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.dndsound.R
import com.example.dndsound.core.model.Mood
import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.WheelPoint
import com.example.dndsound.core.wheel.Tier
import com.example.dndsound.core.wheel.WheelColor
import com.example.dndsound.core.wheel.WheelConfig
import com.example.dndsound.core.wheel.WheelMath
import com.example.dndsound.core.wheel.WheelZone
import com.example.dndsound.core.wheel.WheelZonePalette
import com.example.dndsound.core.wheel.WheelZones
import com.example.dndsound.ui.zoneName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The 25-zone mood wheel: an OKLab gradient bitmap (sector colors blended
 * through explicit transition colors, muted toward the neutral center), thin
 * service lines for the center/tier-split rings, an active-zone highlight and
 * sector labels around the rim.
 *
 * Screen y grows downward while the wheel convention is math-style (CCW,
 * 0 = east), so every drawn angle is mirrored: screenStart = -mathAngle.
 *
 * Gestures: tap/drag places the marker, double-tap returns it to the center.
 * Crossing a zone boundary gives a haptic tick. TalkBack exposes the current
 * zone name plus rotate/inward/outward actions.
 */

/** Fraction of gray mixed into zones that have no tracks. */
private const val EMPTY_DESATURATION = 0.35f

/**
 * Gradient raster size in px. The bitmap is rendered once at this fixed size
 * and scaled up by the Canvas with high-quality filtering — a smooth gradient
 * hides the upsampling completely, while keeping the rasterization (OKLab math
 * per pixel) around 100k pixels instead of the full canvas (1M+ on a phone).
 */
private const val GRADIENT_BITMAP_PX = 320

@Composable
fun MoodWheel(
    marker: WheelPoint?,
    mode: MusicMode,
    emptyZoneIds: Set<String>,
    desaturateEmpty: Boolean,
    onPointChange: (WheelPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val dimLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = if (mode == MusicMode.BATTLE) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val backdropColor = MaterialTheme.colorScheme.surfaceContainerLow
    val outlineColor = MaterialTheme.colorScheme.outline
    val markerColor = MaterialTheme.colorScheme.primary
    val neutralLabel = stringResource(R.string.zone_neutral)
    val moodLabels = moodLabels()
    val config = WheelConfig()

    // Gradient bitmap is decoupled from the canvas size: rendered once at a
    // fixed small resolution whenever the empty-zone set changes, reused at
    // any canvas size through the shader.
    var gradient by remember { mutableStateOf<GradientLayer?>(null) }
    LaunchedEffect(emptyZoneIds, desaturateEmpty) {
        gradient = withContext(Dispatchers.Default) {
            GradientLayer(renderWheelBitmap(GRADIENT_BITMAP_PX, emptyZoneIds, desaturateEmpty))
        }
    }

    val haptics = LocalHapticFeedback.current
    var lastZoneId by remember { mutableStateOf<String?>(null) }
    fun report(point: WheelPoint) {
        val zoneId = WheelZones.zoneAt(point, config).id
        if (lastZoneId != zoneId) {
            lastZoneId = zoneId
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onPointChange(point)
    }

    val currentZoneName = zoneName(
        (marker ?: WheelPoint(0.7f, 0f)).let { WheelZones.zoneAt(it, config) },
    )
    val nextCwLabel = stringResource(R.string.zone_next_cw)
    val nextCcwLabel = stringResource(R.string.zone_next_ccw)
    val outwardLabel = stringResource(R.string.zone_outward)
    val inwardLabel = stringResource(R.string.zone_inward)

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { report(WheelPoint.CENTER) },
                    onTap = { offset -> report(offset.toWheelPoint(size, config)) },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> report(offset.toWheelPoint(size, config)) },
                    onDrag = { change, _ ->
                        change.consume()
                        report(change.position.toWheelPoint(size, config))
                    },
                )
            }
            .semantics {
                stateDescription = currentZoneName
                customActions = listOf(
                    CustomAccessibilityAction(nextCwLabel) {
                        rotateMarker(marker, +45f)?.let(::report); true
                    },
                    CustomAccessibilityAction(nextCcwLabel) {
                        rotateMarker(marker, -45f)?.let(::report); true
                    },
                    CustomAccessibilityAction(outwardLabel) {
                        moveMarkerRadially(marker)?.let(::report); true
                    },
                    CustomAccessibilityAction(inwardLabel) {
                        report(WheelPoint.CENTER); true
                    },
                )
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * WHEEL_MARGIN

        drawCircle(color = backdropColor, radius = radius, center = center)
        gradient?.let { layer ->
            // The shader on an antialiased circle keeps the rim smooth at any
            // resolution — upscaling the raw bitmap would leave a jagged edge.
            drawIntoCanvas { canvas ->
                layer.matrix.setScale(radius * 2f / GRADIENT_BITMAP_PX, radius * 2f / GRADIENT_BITMAP_PX)
                layer.matrix.postTranslate(center.x - radius, center.y - radius)
                layer.shader.setLocalMatrix(layer.matrix)
                layer.paint.shader = layer.shader
                canvas.nativeCanvas.drawCircle(center.x, center.y, radius, layer.paint)
            }
        }

        // Service lines: center ring, dashed tier split, transition band edges.
        drawCircle(
            color = outlineColor.copy(alpha = 0.55f),
            radius = radius * config.centerRadius,
            center = center,
            style = Stroke(width = 1.5f),
        )
        drawCircle(
            color = outlineColor.copy(alpha = 0.35f),
            radius = radius * config.tierSplitRadius,
            center = center,
            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f))),
        )
        WheelZonesBoundaryAngles.forEach { angle ->
            listOf(angle - config.transitionHalfWidthDeg, angle + config.transitionHalfWidthDeg).forEach { edge ->
                drawRadialLine(center, radius, edge, config.centerRadius, 1f, outlineColor.copy(alpha = 0.14f))
            }
        }
        drawCircle(
            color = accentColor.copy(alpha = 0.5f),
            radius = radius,
            center = center,
            style = Stroke(width = 2f),
        )

        // Active zone highlight: a gentle brightening plus an outline.
        marker?.let { point ->
            when (val zone = WheelZones.zoneAt(point, config)) {
                WheelZone.Neutral -> {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.10f),
                        radius = radius * config.centerRadius,
                        center = center,
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = 0.8f),
                        radius = radius * config.centerRadius,
                        center = center,
                        style = Stroke(width = 2f),
                    )
                }

                is WheelZone.Sector -> {
                    val (rInner, rOuter) = WheelZones.tierRadii(zone.tier, config)
                    val half = WheelZones.sectorClearHalfDeg(config)
                    drawAnnulusSector(
                        center, radius,
                        mathCenterAngle = zone.mood.angleDeg,
                        halfSpanDeg = half,
                        rInner = rInner, rOuter = rOuter,
                        fillColor = Color.White.copy(alpha = 0.12f),
                        outlineColor = accentColor.copy(alpha = 0.75f),
                    )
                }

                is WheelZone.Transition -> {
                    val boundary = WheelZones.fold360(WheelZones.boundaryAngleOf(zone.a, zone.b))
                    drawAnnulusSector(
                        center, radius,
                        mathCenterAngle = boundary,
                        halfSpanDeg = config.transitionHalfWidthDeg,
                        rInner = config.centerRadius, rOuter = 1f,
                        fillColor = Color.White.copy(alpha = 0.12f),
                        outlineColor = accentColor.copy(alpha = 0.75f),
                    )
                }
            }
        }

        // Rim labels: eight sector names, double-drawn for readability.
        Mood.entries.forEach { mood ->
            val angleRad = Math.toRadians(mood.angleDeg.toDouble())
            val pos = Offset(
                center.x + radius * 0.72f * cos(angleRad).toFloat(),
                center.y - radius * 0.72f * sin(angleRad).toFloat(),
            )
            drawLabel(textMeasurer, moodLabels.getValue(mood), pos, 14.sp, labelColor)
        }
        drawLabel(textMeasurer, neutralLabel, center, 11.sp, dimLabelColor)

        marker?.let { point ->
            val angleRad = Math.toRadians(point.angleDeg.toDouble())
            val markerPos = Offset(
                center.x + point.radius * radius * cos(angleRad).toFloat(),
                center.y - point.radius * radius * sin(angleRad).toFloat(),
            )
            drawCircle(color = markerColor.copy(alpha = 0.25f), radius = 24f, center = markerPos)
            drawCircle(color = markerColor, radius = 13f, center = markerPos)
            drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 5f, center = markerPos)
        }
    }
}

// ---------------------------------------------------------------- drawing

/** Boundary angles between the eight sectors, in math convention (CCW, east = 0). */
private val WheelZonesBoundaryAngles: List<Float> = buildList {
    val moods = Mood.entries
    for (i in moods.indices) {
        add(WheelZones.fold360(WheelZones.boundaryAngleOf(moods[i], moods[(i + 1) % moods.size])))
    }
}

private fun DrawScope.drawRadialLine(
    center: Offset,
    radius: Float,
    mathAngleDeg: Float,
    rInner: Float,
    rOuter: Float,
    color: Color,
) {
    val a = Math.toRadians(mathAngleDeg.toDouble())
    drawLine(
        color = color,
        start = Offset(
            center.x + radius * rInner * cos(a).toFloat(),
            center.y - radius * rInner * sin(a).toFloat(),
        ),
        end = Offset(
            center.x + radius * rOuter * cos(a).toFloat(),
            center.y - radius * rOuter * sin(a).toFloat(),
        ),
        strokeWidth = 1.5f,
    )
}

/**
 * Fills an annular sector (ring slice) with one thick arc stroke centered on
 * the mid radius, then outlines both edges and the two radial cuts.
 */
private fun DrawScope.drawAnnulusSector(
    center: Offset,
    radius: Float,
    mathCenterAngle: Float,
    halfSpanDeg: Float,
    rInner: Float,
    rOuter: Float,
    fillColor: Color,
    outlineColor: Color,
) {
    val midR = (rInner + rOuter) / 2f
    val width = (rOuter - rInner) * radius
    // CCW math span [c - h, c + h] mirrors to clockwise screen arc.
    val screenStart = -(mathCenterAngle + halfSpanDeg)
    drawArc(
        color = fillColor,
        startAngle = screenStart,
        sweepAngle = halfSpanDeg * 2,
        useCenter = false,
        topLeft = Offset(center.x - radius * midR, center.y - radius * midR),
        size = Size(radius * midR * 2, radius * midR * 2),
        style = Stroke(width = width),
    )
    listOf(rOuter, rInner).forEach { r ->
        drawArc(
            color = outlineColor,
            startAngle = screenStart,
            sweepAngle = halfSpanDeg * 2,
            useCenter = false,
            topLeft = Offset(center.x - radius * r, center.y - radius * r),
            size = Size(radius * r * 2, radius * r * 2),
            style = Stroke(width = 1.5f),
        )
    }
    listOf(mathCenterAngle - halfSpanDeg, mathCenterAngle + halfSpanDeg).forEach { edge ->
        drawRadialLine(center, radius, edge, rInner, rOuter, outlineColor)
    }
}

private fun DrawScope.drawLabel(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    pos: Offset,
    fontSize: TextUnit,
    color: Color,
) {
    val measured = textMeasurer.measure(
        text,
        TextStyle(fontSize = fontSize, color = color, fontFamily = FontFamily.Serif),
    )
    val topLeft = Offset(pos.x - measured.size.width / 2f, pos.y - measured.size.height / 2f)
    // Dark halo first so labels stay readable over bright gradient parts.
    drawText(
        textLayoutResult = measured,
        color = Color.Black.copy(alpha = 0.55f),
        topLeft = topLeft + Offset(1f, 1f),
    )
    drawText(textLayoutResult = measured, topLeft = topLeft)
}

// ----------------------------------------------------------------- raster

/**
 * Cached gradient bitmap with the machinery to paint it through a BitmapShader:
 * bilinear filtering ([Paint.FILTER_BITMAP_FLAG]) upscales the small raster,
 * antialiased circle geometry keeps the rim smooth at display resolution.
 */
private class GradientLayer(bitmap: Bitmap) {
    val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val matrix = Matrix()
}

/** Renders the wheel gradient into a square bitmap (one cached pass). */
private fun renderWheelBitmap(sizePx: Int, emptyZoneIds: Set<String>, desaturateEmpty: Boolean): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(sizePx * sizePx)
    val c = sizePx / 2f
    // Wheel radius 1.0 maps to the bitmap edge: the shader circle cuts the
    // square exactly at the wheel boundary.
    val r = sizePx / 2f
    val anchors = WheelZonePalette.colorAnchors()
    val neutral = WheelZonePalette.neutralColor
    val config = WheelConfig()
    val checkEmpty = desaturateEmpty && emptyZoneIds.isNotEmpty()
    var index = 0
    for (y in 0 until sizePx) {
        for (x in 0 until sizePx) {
            val wx = (x - c) / r
            val wy = -(y - c) / r
            // The whole square is filled: the shader clips it to an
            // antialiased circle, so the bitmap needs no alpha edge (a
            // low-res alpha edge would upscale into visible stair steps).
            val point = WheelPoint(wx, wy)
            var color = WheelColor.at(point, anchors, neutral, config.tierSplitRadius)
            if (checkEmpty && WheelZones.zoneAt(point, config).id in emptyZoneIds) {
                color = desaturate(color, EMPTY_DESATURATION)
            }
            pixels[index++] = color
        }
    }
    bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    return bitmap
}

private fun desaturate(argb: Int, amount: Float): Int {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val gray = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
    fun mix(c: Int): Int = (c + (gray - c) * amount).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (mix(r) shl 16) or (mix(g) shl 8) or mix(b)
}

// -------------------------------------------------------------- gestures

private const val WHEEL_MARGIN = 0.92f

private fun Offset.toWheelPoint(canvasSize: IntSize, config: WheelConfig): WheelPoint {
    val cx = canvasSize.width / 2f
    val cy = canvasSize.height / 2f
    val radius = min(canvasSize.width, canvasSize.height) / 2f * WHEEL_MARGIN
    val x = (this.x - cx) / radius
    val y = -(this.y - cy) / radius
    return WheelPoint(x, y).clamped()
}

/** Marker rotated by [deltaDeg]; nudged out of the neutral center if needed. */
private fun rotateMarker(marker: WheelPoint?, deltaDeg: Float): WheelPoint? {
    val base = marker ?: return null
    val radius = if (base.radius < WheelConfig().centerRadius) 0.4f else base.radius
    return WheelPoint.polar(base.angleDeg + deltaDeg, radius)
}

/** INNER <-> OUTER tier hop, keeping the angle. */
private fun moveMarkerRadially(marker: WheelPoint?): WheelPoint? {
    val base = marker ?: return null
    val config = WheelConfig()
    val target = when {
        base.radius < config.tierSplitRadius -> 0.8f
        else -> 0.4f
    }
    return WheelPoint.polar(base.angleDeg, target)
}

private fun WheelPoint.Companion.polar(angleDeg: Float, radius: Float): WheelPoint {
    val rad = Math.toRadians(angleDeg.toDouble())
    return WheelPoint(
        x = radius * cos(rad).toFloat(),
        y = radius * sin(rad).toFloat(),
    )
}

@Composable
private fun moodLabels(): Map<Mood, String> = mapOf(
    Mood.HAPPY to stringResource(R.string.mood_happy),
    Mood.EPIC to stringResource(R.string.mood_epic),
    Mood.SAD to stringResource(R.string.mood_sad),
    Mood.TENSE to stringResource(R.string.mood_tense),
    Mood.CREEPY to stringResource(R.string.mood_creepy),
    Mood.MYSTIC to stringResource(R.string.mood_mystic),
    Mood.MAGICAL to stringResource(R.string.mood_magical),
    Mood.FUNNY to stringResource(R.string.mood_funny),
)
