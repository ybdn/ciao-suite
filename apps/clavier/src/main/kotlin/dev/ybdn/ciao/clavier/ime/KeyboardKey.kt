package dev.ybdn.ciao.clavier.ime

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.toSize
import dev.ybdn.ciao.designsystem.components.NeoKeyFace
import dev.ybdn.ciao.designsystem.components.NeoTone

/**
 * Délai de l'appui long (accents). Plus court que celui d'Android (400 ms et plus) : en frappe, un
 * appui long doit se sentir tout de suite, comme sur Gboard.
 */
private const val LongPressTimeoutMs = 300L

/** Coordonnées du conteneur du clavier, repère commun des touches et de [KeyOverlay]. */
internal class KeyboardContainer {
    var coordinates: LayoutCoordinates? = null

    val width: Float get() = coordinates?.size?.width?.toFloat() ?: 0f
}

/** Position de la touche, lue au moment de l'appui : pas un état Compose, rien à recomposer. */
private class KeyCoordinates {
    var value: LayoutCoordinates? = null
}

/**
 * Ce que fait une touche au toucher. Les positions sont dans le repère de [KeyboardContainer].
 * [onLongPress] null : pas d'appui long, le relâcher déclenche [onUp] quel que soit le délai.
 */
internal class KeyTouch(
    val onDown: (key: Rect, position: Offset) -> Unit = { _, _ -> },
    val onLongPress: (() -> Unit)? = null,
    val onMove: (position: Offset) -> Unit = {},
    val onUp: () -> Unit = {},
    val onCancel: () -> Unit = {},
    /** Activation par TalkBack (double appui en exploration tactile), sans geste. */
    val onAccessibilityClick: () -> Unit,
)

/**
 * Touche du clavier : [NeoKeyFace] dans un conteneur fixe qui reçoit les gestes. On ne passe pas par
 * `combinedClickable` (cf. `NeoKey`) : les accents se choisissent en glissant après l'appui long,
 * l'espace déplace le curseur en glissant et le retour arrière se répète tant que le doigt reste
 * posé, ce qui demande de suivre le doigt du début à la fin. Seule cette touche se recompose à
 * l'appui (spec §5.4).
 */
@Composable
internal fun RowScope.KeyboardKey(
    weight: Float,
    tone: NeoTone,
    container: KeyboardContainer,
    touch: KeyTouch,
    enabled: Boolean = true,
    description: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val coordinates = remember { KeyCoordinates() }
    val currentTouch by rememberUpdatedState(touch)

    fun boundsInContainer(): Rect {
        val key = coordinates.value ?: return Rect.Zero
        val parent = container.coordinates ?: return Rect(Offset.Zero, key.size.toSize())
        return parent.localBoundingBoxOf(key, clipBounds = false)
    }

    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .onGloballyPositioned { coordinates.value = it }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (description != null) contentDescription = description
                if (enabled) {
                    onClick { currentTouch.onAccessibilityClick(); true }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val key = boundsInContainer()
                    pressed = true
                    currentTouch.onDown(key, key.topLeft + down.position)
                    var released = false
                    try {
                        var longPressPending = currentTouch.onLongPress != null
                        var lastUptime = down.uptimeMillis
                        while (true) {
                            val event = if (longPressPending) {
                                val remaining = down.uptimeMillis + LongPressTimeoutMs - lastUptime
                                withTimeoutOrNull(remaining.coerceAtLeast(0L)) { awaitPointerEvent() }
                            } else {
                                awaitPointerEvent()
                            }
                            if (event == null) {
                                longPressPending = false
                                currentTouch.onLongPress?.invoke()
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                            lastUptime = change.uptimeMillis
                            if (change.changedToUp() || !change.pressed) {
                                change.consume()
                                released = true
                                break
                            }
                            if (change.positionChanged()) {
                                change.consume()
                                currentTouch.onMove(key.topLeft + change.position)
                            }
                        }
                    } finally {
                        pressed = false
                        if (released) currentTouch.onUp() else currentTouch.onCancel()
                    }
                }
            },
    ) {
        NeoKeyFace(pressed = pressed, modifier = Modifier.fillMaxSize(), tone = tone, enabled = enabled, content = content)
    }
}
