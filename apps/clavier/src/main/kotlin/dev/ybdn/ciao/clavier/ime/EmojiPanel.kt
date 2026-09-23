package dev.ybdn.ciao.clavier.ime

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ybdn.ciao.clavier.R
import dev.ybdn.ciao.clavier.domain.emoji.EmojiCategory
import dev.ybdn.ciao.clavier.domain.emoji.EmojiGroup
import dev.ybdn.ciao.designsystem.components.NeoKeyFace
import dev.ybdn.ciao.designsystem.components.NeoTone
import dev.ybdn.ciao.designsystem.theme.NeoTheme

/** Données du panneau, fournies par le service (catalogue filtré, récents, couleurs de peau). */
@Immutable
data class EmojiPanelData(
    val categories: List<EmojiCategory> = emptyList(),
    val recents: List<String> = emptyList(),
    /** Emoji de base → dernière couleur de peau choisie. */
    val skinTones: Map<String, String> = emptyMap(),
)

/** Onglets du panneau : les récents, puis les catégories Unicode (spec §8). */
private enum class EmojiTab(val icon: String, @StringRes val label: Int, val group: EmojiGroup?) {
    Recents("🕘", R.string.emoji_tab_recents, null),
    Smileys("😀", R.string.emoji_tab_smileys, EmojiGroup.Smileys),
    People("👋", R.string.emoji_tab_people, EmojiGroup.People),
    Animals("🐻", R.string.emoji_tab_animals, EmojiGroup.Animals),
    Food("🍔", R.string.emoji_tab_food, EmojiGroup.Food),
    Activities("⚽", R.string.emoji_tab_activities, EmojiGroup.Activities),
    Travel("🚗", R.string.emoji_tab_travel, EmojiGroup.Travel),
    Objects("💡", R.string.emoji_tab_objects, EmojiGroup.Objects),
    Symbols("🔣", R.string.emoji_tab_symbols, EmojiGroup.Symbols),
    Flags("🏁", R.string.emoji_tab_flags, EmojiGroup.Flags),
}

private val EmojiCellHeight = 48.dp
private const val EmojiColumns = 8
private val EmojiFontSize = 26.sp
private val TabFontSize = 18.sp
private val TabRowHeight = 48.dp
private val TabRowGap = 8.dp

/** Case d'emoji, pour l'identité de sa fenêtre de couleurs de peau. */
private data class EmojiCellId(val base: String)

/**
 * Panneau emojis (apps/clavier/docs/spec-v1.md §8), à la place des touches et à la même hauteur
 * ([height]) : grille de la catégorie choisie, puis une rangée « ABC », onglets, retour arrière.
 * Appui long sur un emoji qui a des couleurs de peau : fenêtre de variantes, choisies en glissant ;
 * la dernière choisie devient celle de la case.
 */
@Composable
internal fun EmojiPanel(
    height: Dp,
    data: EmojiPanelData,
    container: KeyboardContainer,
    overlay: KeyOverlayState,
    variantGeometry: (key: Rect, variants: List<String>, containerWidth: Float) -> VariantPopupGeometry,
    actions: KeyboardActions,
    onClose: () -> Unit,
) {
    var tab by remember { mutableStateOf(if (data.recents.isEmpty()) EmojiTab.Smileys else EmojiTab.Recents) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(tab) { gridState.scrollToItem(0) }

    Column(modifier = Modifier.fillMaxWidth().height(height)) {
        val cells: List<EmojiCell> = remember(tab, data) {
            if (tab == EmojiTab.Recents) {
                data.recents.map { EmojiCell(base = it, variants = emptyList()) }
            } else {
                data.categories.firstOrNull { it.group == tab.group }?.emojis.orEmpty().map { emoji ->
                    EmojiCell(
                        base = emoji.value,
                        variants = if (emoji.skinTones.isEmpty()) emptyList() else listOf(emoji.value) + emoji.skinTones,
                        shown = data.skinTones[emoji.value] ?: emoji.value,
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (cells.isEmpty() && tab == EmojiTab.Recents) {
                Text(
                    stringResource(R.string.emoji_recents_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeoTheme.palette.content,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(EmojiColumns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(cells, key = { it.base }) { cell ->
                        EmojiCellView(cell, container, overlay, variantGeometry, actions)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TabRowHeight)
                .padding(top = TabRowGap / 2, end = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KeyboardKey(
                weight = 1.5f,
                tone = NeoTone.Muted,
                container = container,
                touch = KeyTouch(
                    onDown = { _, _ -> actions.keyFeedback() },
                    onUp = onClose,
                    onAccessibilityClick = onClose,
                ),
            ) {
                Text("ABC", style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp))
            }
            for (candidate in EmojiTab.entries) {
                EmojiTabView(candidate, selected = candidate == tab, onSelect = { tab = candidate })
            }
            BackspaceKey(weight = 1.5f, container = container, actions = actions)
        }
    }
}

@Immutable
private data class EmojiCell(val base: String, val variants: List<String>, val shown: String = base)

@Composable
private fun EmojiCellView(
    cell: EmojiCell,
    container: KeyboardContainer,
    overlay: KeyOverlayState,
    variantGeometry: (key: Rect, variants: List<String>, containerWidth: Float) -> VariantPopupGeometry,
    actions: KeyboardActions,
) {
    val owner = EmojiCellId(cell.base)
    val keyBounds = remember { CellBounds() }
    val palette = NeoTheme.palette

    fun choose(emoji: String) {
        actions.emojiTyped(emoji)
        if (cell.variants.isNotEmpty()) actions.skinToneChosen(cell.base, emoji)
    }

    KeyTouchBox(
        modifier = Modifier.fillMaxWidth().height(EmojiCellHeight),
        container = container,
        description = null,
        touch = KeyTouch(
            onDown = { bounds, _ ->
                actions.keyFeedback()
                keyBounds.value = bounds
            },
            onLongPress = if (cell.variants.isEmpty()) null else {
                {
                    actions.keyFeedback()
                    overlay.showVariants(owner, cell.variants, variantGeometry(keyBounds.value, cell.variants, container.width))
                }
            },
            onMove = { position -> overlay.selectVariantAt(owner, position) },
            onUp = {
                if (overlay.hasVariants(owner)) {
                    overlay.closeVariants(owner)?.let(::choose)
                } else {
                    actions.emojiTyped(cell.shown)
                }
            },
            onCancel = { overlay.closeVariants(owner) },
            onAccessibilityClick = { actions.emojiTyped(cell.shown) },
        ),
    ) { pressed ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (pressed) Modifier.background(palette.surfaceMuted) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(cell.shown, fontSize = EmojiFontSize)
        }
    }
}

/** Position de la case au moment de l'appui : pas un état Compose, rien à recomposer. */
private class CellBounds {
    var value = Rect.Zero
}

/** Onglet : l'actif prend la face de touche « sélection » (bordure et ombre, spec §5.1, §5.2). */
@Composable
private fun RowScope.EmojiTabView(tab: EmojiTab, selected: Boolean, onSelect: () -> Unit) {
    val label = stringResource(tab.label)
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onSelect,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            NeoKeyFace(pressed = false, modifier = Modifier.fillMaxSize(), tone = NeoTone.Selected) {
                Text(tab.icon, fontSize = TabFontSize)
            }
        } else {
            Text(tab.icon, fontSize = TabFontSize)
        }
    }
}
