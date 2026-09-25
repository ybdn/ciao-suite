package dev.ybdn.ciao.galerie.presentation.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.ybdn.ciao.galerie.domain.model.EditRecipe
import dev.ybdn.ciao.galerie.domain.model.NormalizedRect
import dev.ybdn.ciao.galerie.domain.util.CropHandle
import kotlin.math.abs
import kotlin.math.min

/**
 * Aperçu de la recette : image de travail orientée, redressée, puis soit affichée en entier avec le
 * cadre de recadrage manipulable ([cropMode]), soit recadrée. Les réglages sont appliqués à l'image
 * seule par le shader AGSL ([shaderEffect]) ; le cadre est dessiné sur une couche distincte.
 *
 * @param imageSize taille intrinsèque de l'image de travail (orientation EXIF appliquée).
 * @param shaderEffect effet de rendu des réglages pour un cadre donné (pixels de la vue), null si neutre.
 */
@Composable
fun EditorCanvas(
    painter: Painter,
    imageSize: Size,
    recipe: EditRecipe,
    cropMode: Boolean,
    showGrid: Boolean,
    modifier: Modifier = Modifier,
    shaderEffect: ((frame: Rect) -> android.graphics.RenderEffect)? = null,
    onGestureStart: () -> Unit = {},
    onGestureEnd: () -> Unit = {},
    onResize: (CropHandle, dx: Double, dy: Double) -> Unit = { _, _, _ -> },
    onMove: (dx: Double, dy: Double) -> Unit = { _, _ -> },
    onZoom: (Double) -> Unit = {},
) {
    val density = LocalDensity.current
    val padding = with(density) { CROP_PADDING.toPx() }
    val touchSlop = with(density) { HANDLE_TOUCH_RADIUS.toPx() }
    val currentRecipe = rememberUpdatedState(recipe)

    BoxWithConstraints(modifier = modifier) {
        val viewSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val layout = EditorLayout(viewSize, imageSize, recipe, cropMode, padding)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = shaderEffect?.invoke(layout.cropRect)?.asComposeRenderEffect()
                },
        ) {
            if (cropMode) {
                drawImage(painter, layout)
            } else {
                clipRect(layout.cropRect.left, layout.cropRect.top, layout.cropRect.right, layout.cropRect.bottom) {
                    drawImage(painter, layout)
                }
            }
        }
        if (cropMode) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(imageSize, padding) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val start = EditorLayout(
                                Size(size.width.toFloat(), size.height.toFloat()),
                                imageSize,
                                currentRecipe.value,
                                cropMode = true,
                                padding,
                            )
                            val handle = start.handleAt(down.position, touchSlop)
                            if (handle == null && !start.cropRect.contains(down.position)) return@awaitEachGesture
                            onGestureStart()
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    val zoom = event.calculateZoom()
                                    if (zoom != 1f) onZoom(zoom.toDouble())
                                } else {
                                    val delta = pressed.first().positionChange()
                                    if (delta != Offset.Zero) {
                                        val dx = (delta.x / start.orientedScaledWidth).toDouble()
                                        val dy = (delta.y / start.orientedScaledHeight).toDouble()
                                        if (handle != null) onResize(handle, dx, dy) else onMove(dx, dy)
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            }
                            onGestureEnd()
                        }
                    },
            ) {
                drawCropFrame(layout, showGrid)
            }
        }
    }
}

/** Placement de l'image et du cadre dans la zone d'aperçu. */
private class EditorLayout(
    viewSize: Size,
    val imageSize: Size,
    val recipe: EditRecipe,
    cropMode: Boolean,
    padding: Float,
) {
    private val swaps = recipe.transform.swapsDimensions
    val orientedWidth = if (swaps) imageSize.height else imageSize.width
    val orientedHeight = if (swaps) imageSize.width else imageSize.height

    /** Partie de l'image orientée visible : tout le cadre en recadrage, sinon le résultat. */
    private val viewport: NormalizedRect = if (cropMode) NormalizedRect.FULL else recipe.crop
    private val margin = if (cropMode) padding else 0f

    val scale: Float = min(
        (viewSize.width - 2 * margin) / (viewport.width.toFloat() * orientedWidth),
        (viewSize.height - 2 * margin) / (viewport.height.toFloat() * orientedHeight),
    )
    val center = Offset(viewSize.width / 2, viewSize.height / 2)
    val orientedScaledWidth = orientedWidth * scale
    val orientedScaledHeight = orientedHeight * scale

    /** Coin haut gauche de l'image orientée dans la vue. */
    val origin = Offset(
        center.x - viewport.centerX.toFloat() * orientedScaledWidth,
        center.y - viewport.centerY.toFloat() * orientedScaledHeight,
    )

    val cropRect = Rect(
        left = origin.x + recipe.crop.left.toFloat() * orientedScaledWidth,
        top = origin.y + recipe.crop.top.toFloat() * orientedScaledHeight,
        right = origin.x + recipe.crop.right.toFloat() * orientedScaledWidth,
        bottom = origin.y + recipe.crop.bottom.toFloat() * orientedScaledHeight,
    )

    fun handleAt(point: Offset, radius: Float): CropHandle? {
        val nearLeft = abs(point.x - cropRect.left) <= radius
        val nearRight = abs(point.x - cropRect.right) <= radius
        val nearTop = abs(point.y - cropRect.top) <= radius
        val nearBottom = abs(point.y - cropRect.bottom) <= radius
        val withinX = point.x in (cropRect.left - radius)..(cropRect.right + radius)
        val withinY = point.y in (cropRect.top - radius)..(cropRect.bottom + radius)
        return when {
            nearLeft && nearTop -> CropHandle.TOP_LEFT
            nearRight && nearTop -> CropHandle.TOP_RIGHT
            nearRight && nearBottom -> CropHandle.BOTTOM_RIGHT
            nearLeft && nearBottom -> CropHandle.BOTTOM_LEFT
            nearTop && withinX -> CropHandle.TOP
            nearBottom && withinX -> CropHandle.BOTTOM
            nearLeft && withinY -> CropHandle.LEFT
            nearRight && withinY -> CropHandle.RIGHT
            else -> null
        }
    }
}

/** Image orientée (miroir puis quart de tour), redressée autour du centre de l'image orientée. */
private fun DrawScope.drawImage(painter: Painter, layout: EditorLayout) {
    val transform = layout.recipe.transform
    withTransform({
        translate(
            layout.origin.x + layout.orientedScaledWidth / 2,
            layout.origin.y + layout.orientedScaledHeight / 2,
        )
        rotate(layout.recipe.straightenDegrees.toFloat(), pivot = Offset.Zero)
        rotate(transform.rotationDegrees.toFloat(), pivot = Offset.Zero)
        scale(if (transform.flipped) -layout.scale else layout.scale, layout.scale, pivot = Offset.Zero)
        translate(-layout.imageSize.width / 2, -layout.imageSize.height / 2)
    }) {
        with(painter) { draw(layout.imageSize) }
    }
}

private fun DrawScope.drawCropFrame(layout: EditorLayout, showGrid: Boolean) {
    val crop = layout.cropRect
    // Hors du cadre : assombri.
    clipRect(crop.left, crop.top, crop.right, crop.bottom, clipOp = ClipOp.Difference) {
        drawRect(SCRIM)
    }
    val stroke = 2.dp.toPx()
    drawRect(Color.White, topLeft = crop.topLeft, size = crop.size, style = Stroke(stroke))
    if (showGrid) {
        for (i in 1..2) {
            val x = crop.left + crop.width * i / 3
            val y = crop.top + crop.height * i / 3
            drawLine(GRID, Offset(x, crop.top), Offset(x, crop.bottom), strokeWidth = 1.dp.toPx())
            drawLine(GRID, Offset(crop.left, y), Offset(crop.right, y), strokeWidth = 1.dp.toPx())
        }
    }
    val length = 22.dp.toPx()
    val thick = 5.dp.toPx()
    listOf(
        crop.topLeft to Offset(1f, 1f),
        crop.topRight to Offset(-1f, 1f),
        crop.bottomRight to Offset(-1f, -1f),
        crop.bottomLeft to Offset(1f, -1f),
    ).forEach { (corner, direction) ->
        drawLine(Color.White, corner, corner + Offset(length * direction.x, 0f), strokeWidth = thick)
        drawLine(Color.White, corner, corner + Offset(0f, length * direction.y), strokeWidth = thick)
    }
}

private val CROP_PADDING = 24.dp
private val HANDLE_TOUCH_RADIUS = 28.dp
private val SCRIM = Color.Black.copy(alpha = 0.55f)
private val GRID = Color.White.copy(alpha = 0.6f)
