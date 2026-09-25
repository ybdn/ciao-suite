package dev.ybdn.ciaocloud.presentation.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.ybdn.ciao.designsystem.components.BorderWidth
import dev.ybdn.ciao.designsystem.components.neoSurface
import dev.ybdn.ciao.designsystem.theme.Ink
import dev.ybdn.ciao.designsystem.theme.LabelMono
import dev.ybdn.ciao.designsystem.theme.Selected
import dev.ybdn.ciao.designsystem.theme.NeoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Pincement à deux doigts pour changer le nombre de colonnes. Les gestes à un doigt ne sont pas
 * consommés : le défilement et les appuis sur les vignettes restent intacts.
 */
fun Modifier.pinchToChangeColumns(onZoomIn: () -> Unit, onZoomOut: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoom = 1f
        var handled = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                zoom *= event.calculateZoom()
                // Bloque le défilement de la grille pendant le pincement.
                event.changes.forEach { it.consume() }
                if (!handled && zoom > ZOOM_IN_THRESHOLD) {
                    onZoomIn()
                    handled = true
                } else if (!handled && zoom < ZOOM_OUT_THRESHOLD) {
                    onZoomOut()
                    handled = true
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * Barre de défilement rapide : poignée déplaçable sur le bord droit, avec repère mois/année
 * pendant le déplacement. Masquée au repos.
 */
@Composable
fun FastScroller(
    gridState: LazyGridState,
    labelForIndex: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }

    val scrollFraction by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val scrollable = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(1)
            (gridState.firstVisibleItemIndex.toFloat() / scrollable).coerceIn(0f, 1f)
        }
    }
    val isScrollable by remember {
        derivedStateOf { gridState.canScrollForward || gridState.canScrollBackward }
    }

    LaunchedEffect(gridState.isScrollInProgress, isDragging) {
        if (gridState.isScrollInProgress || isDragging) {
            visible = true
        } else {
            delay(HIDE_DELAY_MS)
            visible = false
        }
    }

    if (!isScrollable) return

    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(TRACK_WIDTH + LABEL_MAX_WIDTH)) {
        val trackHeightPx = with(density) { maxHeight.toPx() }
        val thumbHeightPx = with(density) { THUMB_HEIGHT.toPx() }
        val fraction = if (isDragging) dragFraction else scrollFraction
        val thumbOffsetPx = (fraction * (trackHeightPx - thumbHeightPx)).roundToInt()

        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, thumbOffsetPx) },
                ) {
                    if (isDragging) {
                        val index = (dragFraction * (gridState.layoutInfo.totalItemsCount - 1)).roundToInt()
                        Text(
                            text = labelForIndex(index),
                            style = LabelMono,
                            color = Ink,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .neoSurface(Selected, NeoTheme.palette.outline, shadowOffset = 3.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(width = TRACK_WIDTH, height = THUMB_HEIGHT)
                            .padding(end = 4.dp)
                            .background(NeoTheme.palette.outline, RectangleShape)
                            .padding(start = BorderWidth, top = BorderWidth, bottom = BorderWidth)
                            .background(Selected, RectangleShape)
                            .pointerInput(trackHeightPx) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        dragFraction = scrollFraction
                                        isDragging = true
                                    },
                                    onDragEnd = { isDragging = false },
                                    onDragCancel = { isDragging = false },
                                ) { change, dragAmount ->
                                    change.consume()
                                    dragFraction = (dragFraction + dragAmount / (trackHeightPx - thumbHeightPx))
                                        .coerceIn(0f, 1f)
                                    val target = (dragFraction * (gridState.layoutInfo.totalItemsCount - 1)).roundToInt()
                                    scope.launch { gridState.scrollToItem(target.coerceAtLeast(0)) }
                                }
                            },
                    )
                }
            }
        }
    }
}

private const val ZOOM_IN_THRESHOLD = 1.25f
private const val ZOOM_OUT_THRESHOLD = 0.8f
private const val HIDE_DELAY_MS = 1_500L
private val TRACK_WIDTH = 28.dp
private val THUMB_HEIGHT = 52.dp
private val LABEL_MAX_WIDTH = 180.dp
