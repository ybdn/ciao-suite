package dev.ybdn.ciaocloud.presentation.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ybdn.ciao.designsystem.components.BorderWidth
import dev.ybdn.ciao.designsystem.theme.Ink
import dev.ybdn.ciao.designsystem.theme.LabelMono
import dev.ybdn.ciao.designsystem.theme.Green
import dev.ybdn.ciao.designsystem.theme.NeoTheme
import kotlin.math.roundToInt

/**
 * Curseur néo-brutaliste : piste bordée, remplissage citron depuis le zéro, curseur carré.
 * Glisser n'importe où sur la piste règle la valeur ; double-appui remet à 0. La valeur est mise en
 * avant pendant le geste.
 */
@Composable
fun AdjustmentSlider(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = NeoTheme.palette
    val currentValue by rememberUpdatedState(value)
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row {
            Text(label.uppercase(), style = LabelMono, color = palette.content, modifier = Modifier.weight(1f))
            Text(
                text = if (value > 0 && min < 0) "+$value" else value.toString(),
                style = if (dragging) MaterialTheme.typography.titleMedium else LabelMono,
                fontWeight = if (dragging) FontWeight.Bold else FontWeight.Normal,
                color = palette.content,
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(min, max) {
                    detectTapGestures(
                        onDoubleTap = {
                            onGestureStart()
                            onChange(0.coerceIn(min, max))
                            onGestureEnd()
                        },
                    )
                }
                .pointerInput(min, max) {
                    fun valueAt(x: Float): Int = (min + (x / size.width).coerceIn(0f, 1f) * (max - min)).roundToInt()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            // Le geste part de la valeur courante (pas de saut au premier contact).
                            dragPosition = (currentValue - min).toFloat() / (max - min) * size.width
                            onGestureStart()
                        },
                        onDragEnd = {
                            dragging = false
                            onGestureEnd()
                        },
                        onDragCancel = {
                            dragging = false
                            onGestureEnd()
                        },
                    ) { change, amount ->
                        change.consume()
                        dragPosition = (dragPosition + amount).coerceIn(0f, size.width.toFloat())
                        onChange(valueAt(dragPosition))
                    }
                },
        ) {
            val trackHeight = 12.dp.toPx()
            val top = (size.height - trackHeight) / 2
            val radius = CornerRadius(4.dp.toPx())
            fun xOf(v: Int) = (v - min).toFloat() / (max - min) * size.width
            drawRoundRect(palette.surface, Offset(0f, top), Size(size.width, trackHeight), radius)
            val zeroX = xOf(0.coerceIn(min, max))
            val valueX = xOf(value)
            drawRect(Green, Offset(minOf(zeroX, valueX), top), Size(kotlin.math.abs(valueX - zeroX), trackHeight))
            drawRoundRect(palette.outline, Offset(0f, top), Size(size.width, trackHeight), radius, style = Stroke(BorderWidth.toPx()))
            val thumb = 22.dp.toPx()
            val thumbLeft = (valueX - thumb / 2).coerceIn(0f, size.width - thumb)
            drawRoundRect(if (dragging) Green else palette.surface, Offset(thumbLeft, (size.height - thumb) / 2), Size(thumb, thumb), radius)
            drawRoundRect(Ink, Offset(thumbLeft, (size.height - thumb) / 2), Size(thumb, thumb), radius, style = Stroke(BorderWidth.toPx()))
        }
    }
}
