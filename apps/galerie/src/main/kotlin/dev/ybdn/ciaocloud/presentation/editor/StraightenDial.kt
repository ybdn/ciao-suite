package dev.ybdn.ciaocloud.presentation.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.ybdn.ciaocloud.domain.util.CropGeometry
import dev.ybdn.ciaocloud.presentation.theme.Coral
import dev.ybdn.ciaocloud.presentation.theme.NeoTheme
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Molette graduée de redressement (−45° à +45°, pas de 0,1°) : glisser horizontalement fait défiler
 * les graduations sous le repère central ; double-appui remet à 0.
 */
@Composable
fun StraightenDial(
    degrees: Double,
    onChange: (Double) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = NeoTheme.palette
    val currentDegrees by rememberUpdatedState(degrees)
    var dragValue by remember { mutableDoubleStateOf(0.0) }
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            String.format(Locale.FRENCH, "%.1f°", degrees),
            style = MaterialTheme.typography.labelLarge,
            color = palette.content,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onChange(0.0) })
                }
                .pointerInput(Unit) {
                    val pixelsPerDegree = PIXELS_PER_DEGREE.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragValue = currentDegrees
                            onDragStart()
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    ) { change, amount ->
                        change.consume()
                        // Glisser vers la gauche fait avancer les graduations : angle croissant.
                        dragValue = (dragValue - amount / pixelsPerDegree)
                            .coerceIn(-CropGeometry.MAX_STRAIGHTEN_DEGREES, CropGeometry.MAX_STRAIGHTEN_DEGREES)
                        onChange((dragValue * 10).roundToInt() / 10.0)
                    }
                },
        ) {
            val pixelsPerDegree = PIXELS_PER_DEGREE.toPx()
            val centerX = size.width / 2
            val visible = (size.width / 2 / pixelsPerDegree).toInt() + 1
            val first = floor(degrees).toInt() - visible
            for (tick in first..(first + 2 * visible + 1)) {
                if (tick < -CropGeometry.MAX_STRAIGHTEN_DEGREES || tick > CropGeometry.MAX_STRAIGHTEN_DEGREES) continue
                val x = centerX + ((tick - degrees) * pixelsPerDegree).toFloat()
                val major = tick % 5 == 0
                val height = if (major) size.height * 0.7f else size.height * 0.4f
                drawLine(
                    color = palette.content.copy(alpha = if (major) 0.9f else 0.45f),
                    start = Offset(x, (size.height - height) / 2),
                    end = Offset(x, (size.height + height) / 2),
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                )
            }
            drawLine(Coral, Offset(centerX, 0f), Offset(centerX, size.height), strokeWidth = 3.dp.toPx())
        }
    }
}

private val PIXELS_PER_DEGREE = 10.dp
