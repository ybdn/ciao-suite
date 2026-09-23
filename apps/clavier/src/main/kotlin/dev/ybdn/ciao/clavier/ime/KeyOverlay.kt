package dev.ybdn.ciao.clavier.ime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ybdn.ciao.designsystem.components.BorderWidth
import dev.ybdn.ciao.designsystem.components.NeoFloatingSurface
import dev.ybdn.ciao.designsystem.components.NeoKeyFace
import dev.ybdn.ciao.designsystem.components.NeoTone
import dev.ybdn.ciao.designsystem.components.ShadowLarge
import kotlin.math.roundToInt

/** Hauteur de l'aperçu de touche et de la fenêtre de variantes, et écart avec la touche. */
private val PopupHeight = 52.dp
private val PopupGap = 4.dp

/** L'aperçu déborde de la touche de chaque côté, pour rester visible autour du doigt. */
private val PreviewOverhang = 6.dp
private val PreviewMinWidth = 44.dp

/** Largeur minimale d'une case de variante (lettre), et d'une variante de plusieurs caractères (« .com »). */
private val VariantMinWidth = 40.dp
private val VariantTextMinWidth = 60.dp

/**
 * Éléments flottants au-dessus des touches (apps/clavier/docs/spec-v1.md §5.1, §6.1) : aperçu de
 * la touche appuyée et fenêtre des variantes par appui long. Dessinés dans la vue du clavier (pas
 * dans une `Popup`, qui ouvrirait une fenêtre de plus depuis celle de l'IME) ; seul [KeyOverlay]
 * lit cet état, pour qu'un appui ne recompose pas tout le clavier. Les positions sont en pixels,
 * dans le repère du conteneur du clavier.
 */
@Stable
internal class KeyOverlayState {

    class Preview(val owner: Any, val label: String, val key: Rect)

    class Variants(val owner: Any, val variants: List<String>, val geometry: VariantPopupGeometry, val selected: Int?)

    var preview by mutableStateOf<Preview?>(null)
        private set

    var variants by mutableStateOf<Variants?>(null)
        private set

    fun showPreview(owner: Any, label: String, key: Rect) {
        preview = Preview(owner, label, key)
    }

    /** Ne masque l'aperçu que s'il appartient encore à [owner] : un autre doigt a pu en poser un. */
    fun hidePreview(owner: Any) {
        if (preview?.owner == owner) preview = null
    }

    fun showVariants(owner: Any, variants: List<String>, geometry: VariantPopupGeometry) {
        hidePreview(owner)
        this.variants = Variants(owner, variants, geometry, selected = 0)
    }

    fun selectVariantAt(owner: Any, position: Offset) {
        val current = variants?.takeIf { it.owner == owner } ?: return
        val selected = current.geometry.indexAt(position)
        if (selected != current.selected) variants = Variants(owner, current.variants, current.geometry, selected)
    }

    /** Ferme la fenêtre de [owner] et renvoie la variante choisie, ou null si l'appui est annulé. */
    fun closeVariants(owner: Any): String? {
        val current = variants?.takeIf { it.owner == owner } ?: return null
        variants = null
        return current.selected?.let { current.variants[it] }
    }

    fun hasVariants(owner: Any): Boolean = variants?.owner == owner
}

/**
 * Géométrie de la fenêtre de variantes, en pixels. La première variante (présélectionnée) est
 * centrée sur la touche quand c'est possible ; la fenêtre est ramenée dans la largeur du clavier.
 * Glisser en dessous de la touche annule le choix. Kotlin pur, testée unitairement.
 */
internal class VariantPopupGeometry(
    val left: Float,
    val top: Float,
    val cellWidth: Float,
    val height: Float,
    val border: Float,
    private val count: Int,
    private val cancelBelow: Float,
) {
    val width: Float get() = count * cellWidth + 2 * border

    fun indexAt(position: Offset): Int? {
        if (position.y > cancelBelow) return null
        val index = ((position.x - left - border) / cellWidth).toInt()
        return index.coerceIn(0, count - 1)
    }

    companion object {
        fun of(
            key: Rect,
            count: Int,
            cellWidth: Float,
            height: Float,
            gap: Float,
            border: Float,
            shadow: Float,
            containerWidth: Float,
        ): VariantPopupGeometry {
            val width = count * cellWidth + 2 * border
            val left = (key.center.x - cellWidth / 2 - border)
                .coerceAtMost(containerWidth - width - shadow)
                .coerceAtLeast(0f)
            val top = (key.top - gap - height).coerceAtLeast(0f)
            return VariantPopupGeometry(
                left = left,
                top = top,
                cellWidth = cellWidth,
                height = height,
                border = border,
                count = count,
                cancelBelow = key.bottom + key.height / 2,
            )
        }
    }
}

/** Calcule la géométrie de la fenêtre de variantes d'une touche, avec les cotes du clavier. */
@Composable
internal fun rememberVariantGeometryFactory(): (key: Rect, variants: List<String>, containerWidth: Float) -> VariantPopupGeometry {
    val density = LocalDensity.current
    return { key, variants, containerWidth ->
        with(density) {
            val minWidth = if (variants.any { it.length > 1 }) VariantTextMinWidth else VariantMinWidth
            VariantPopupGeometry.of(
                key = key,
                count = variants.size,
                cellWidth = maxOf(key.width, minWidth.toPx()),
                height = PopupHeight.toPx(),
                gap = PopupGap.toPx(),
                border = BorderWidth.toPx(),
                shadow = ShadowLarge.toPx(),
                containerWidth = containerWidth,
            )
        }
    }
}

@Composable
internal fun KeyOverlay(state: KeyOverlayState, containerWidth: () -> Float) {
    state.preview?.let { KeyPreview(it, containerWidth()) }
    state.variants?.let { VariantPopup(it) }
}

@Composable
private fun KeyPreview(preview: KeyOverlayState.Preview, containerWidth: Float) {
    val density = LocalDensity.current
    val width = maxOf(preview.key.width + 2 * with(density) { PreviewOverhang.toPx() }, with(density) { PreviewMinWidth.toPx() })
    val height = with(density) { PopupHeight.toPx() }
    val left = (preview.key.center.x - width / 2)
        .coerceAtMost(containerWidth - width - with(density) { ShadowLarge.toPx() })
        .coerceAtLeast(0f)
    val top = (preview.key.top - with(density) { PopupGap.toPx() } - height).coerceAtLeast(0f)
    NeoFloatingSurface(
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { width.toDp() }, with(density) { height.toDp() }),
    ) {
        Text(preview.label, style = MaterialTheme.typography.titleMedium.copy(fontSize = 28.sp))
    }
}

@Composable
private fun VariantPopup(popup: KeyOverlayState.Variants) {
    val density = LocalDensity.current
    val geometry = popup.geometry
    NeoFloatingSurface(
        modifier = Modifier
            .offset { IntOffset(geometry.left.roundToInt(), geometry.top.roundToInt()) }
            .size(with(density) { geometry.width.toDp() }, with(density) { geometry.height.toDp() }),
    ) {
        Row(modifier = Modifier.padding(BorderWidth), verticalAlignment = Alignment.CenterVertically) {
            popup.variants.forEachIndexed { index, variant ->
                Box(
                    modifier = Modifier
                        .width(with(density) { geometry.cellWidth.toDp() })
                        .fillMaxHeight()
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val label = @Composable { Text(variant, style = MaterialTheme.typography.titleMedium.copy(fontSize = 21.sp)) }
                    if (index == popup.selected) {
                        // Variante présélectionnée : rôle « sélection » et bordure, pas la couleur seule (§5.2).
                        NeoKeyFace(pressed = false, modifier = Modifier.matchParentSize(), tone = NeoTone.Selected) { label() }
                    } else {
                        label()
                    }
                }
            }
        }
    }
}
