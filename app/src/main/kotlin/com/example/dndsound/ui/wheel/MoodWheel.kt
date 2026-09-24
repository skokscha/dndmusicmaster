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
        Mood.entries.forEachIndexed { index, mood ->
            // Screen angles run clockwise, so the sector start mirrors the mood angle.
            val start = -mood.angleDeg - SECTOR_HALF_DEG
            drawArc(
                color = accentColor.copy(alpha = if (index % 2 == 0) 0.10f else 0.05f),
                startAngle = start,
                sweepAngle = SECTOR_SWEEP_DEG,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
            )
        }

        val calmRadius = radius * WheelMath.CALM_RADIUS
        drawCircle(color = accentColor.copy(alpha = 0.16f), radius = calmRadius, center = center)
        drawCircle(
            color = accentColor.copy(alpha = 0.5f),
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
            val measured = textMeasurer.measure(name, TextStyle(fontSize = 13.sp, color = labelColor))
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
            drawCircle(color = markerColor, radius = 14f, center = markerPos)
            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = 5f, center = markerPos)
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
