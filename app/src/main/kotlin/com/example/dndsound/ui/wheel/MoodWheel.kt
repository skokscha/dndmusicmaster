package com.example.dndsound.ui.wheel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.example.dndsound.R
import com.example.dndsound.core.model.Mood
import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.WheelPoint
import com.example.dndsound.core.wheel.WheelMath
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The mood wheel: eight mood sectors around a calm center. Drag or tap places
 * the marker; every move reports the normalized wheel point. Screen y grows
 * downward while the wheel convention is math-style (CCW, 0 = east), so the
 * drawn angle is mirrored: screenAngle = -angleDeg.
 *
 * TalkBack actions (next mood / intensity) arrive in the polish stage.
 */

/** Warm per-mood tints so each sector is recognizable at a glance. */
private val MoodTints: Map<Mood, Color> = mapOf(
    Mood.HAPPY to Color(0xFFE8C45A),
    Mood.EPIC to Color(0xFFE07840),
    Mood.SAD to Color(0xFF6E8FBF),
    Mood.TENSE to Color(0xFFC9564B),
    Mood.CREEPY to Color(0xFF9A6EC8),
    Mood.MYSTIC to Color(0xFF5FBFAF),
    Mood.MAGICAL to Color(0xFFC86ED0),
    Mood.FUNNY to Color(0xFF9ACD5A),
)

@Composable
fun MoodWheel(
    marker: WheelPoint?,
    mode: MusicMode,
    onPointChange: (WheelPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val calmLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = if (mode == MusicMode.BATTLE) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val backdropColor = MaterialTheme.colorScheme.surfaceVariant
    val markerColor = MaterialTheme.colorScheme.primary
    val calmName = stringResource(R.string.mood_calm)
    val moodLabels = moodLabels()

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures { offset -> onPointChange(offset.toWheelPoint(size)) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> onPointChange(offset.toWheelPoint(size)) },
                    onDrag = { change, _ ->
                        change.consume()
                        onPointChange(change.position.toWheelPoint(size))
                    },
                )
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * WHEEL_MARGIN

        drawCircle(color = backdropColor, radius = radius, center = center)
        Mood.entries.forEach { mood ->
            val tint = MoodTints.getValue(mood)
            val start = -mood.angleDeg - SECTOR_HALF_DEG
            drawArc(
                color = tint.copy(alpha = 0.20f),
                startAngle = start,
                sweepAngle = SECTOR_SWEEP_DEG,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
            )
            // Radial separator on the sector's clockwise boundary.
            val boundary = Math.toRadians((start).toDouble())
            drawLine(
                color = tint.copy(alpha = 0.45f),
                start = center,
                end = Offset(
                    center.x + radius * cos(boundary).toFloat(),
                    center.y + radius * sin(boundary).toFloat(),
                ),
                strokeWidth = 1.5f,
            )
        }
        drawCircle(
            color = accentColor.copy(alpha = 0.5f),
            radius = radius,
            center = center,
            style = Stroke(width = 2f),
        )

        val calmRadius = radius * WheelMath.CALM_RADIUS
        drawCircle(color = accentColor.copy(alpha = 0.16f), radius = calmRadius, center = center)
        drawCircle(
            color = accentColor.copy(alpha = 0.55f),
            radius = calmRadius,
            center = center,
            style = Stroke(width = 1.5f),
        )

        Mood.entries.forEach { mood ->
            val angleRad = Math.toRadians(mood.angleDeg.toDouble())
            val pos = Offset(
                center.x + radius * 0.72f * cos(angleRad).toFloat(),
                center.y - radius * 0.72f * sin(angleRad).toFloat(),
            )
            val name = moodLabels.getValue(mood)
            val measured = textMeasurer.measure(
                name,
                TextStyle(fontSize = 14.sp, color = labelColor, fontFamily = FontFamily.Serif),
            )
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    pos.x - measured.size.width / 2f,
                    pos.y - measured.size.height / 2f,
                ),
            )
        }
        val calmMeasured = textMeasurer.measure(calmName, TextStyle(fontSize = 11.sp, color = calmLabelColor))
        drawText(
            textLayoutResult = calmMeasured,
            topLeft = Offset(
                center.x - calmMeasured.size.width / 2f,
                center.y - calmMeasured.size.height / 2f,
            ),
        )

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

private const val WHEEL_MARGIN = 0.92f
private const val SECTOR_HALF_DEG = 22.5f
private const val SECTOR_SWEEP_DEG = 45f

private fun Offset.toWheelPoint(canvasSize: IntSize): WheelPoint {
    val cx = canvasSize.width / 2f
    val cy = canvasSize.height / 2f
    val radius = min(canvasSize.width, canvasSize.height) / 2f * WHEEL_MARGIN
    val x = (this.x - cx) / radius
    val y = -(this.y - cy) / radius
    return WheelPoint(x, y).clamped()
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
