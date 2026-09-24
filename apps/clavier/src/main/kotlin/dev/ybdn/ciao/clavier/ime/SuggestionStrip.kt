package dev.ybdn.ciao.clavier.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ybdn.ciao.clavier.R
import dev.ybdn.ciao.clavier.domain.clipboard.ClipboardItem
import dev.ybdn.ciao.designsystem.components.BorderThin
import dev.ybdn.ciao.designsystem.components.NeoKey
import dev.ybdn.ciao.designsystem.components.NeoTone
import dev.ybdn.ciao.designsystem.theme.NeoTheme

/** Une proposition de la barre. */
data class SuggestionItem(val text: String, val kind: Kind) {
    enum class Kind {
        /** Le mot tel que tapé, pour le garder quand l'autocorrection va le remplacer. */
        Typed,
        Word,

        /** Le mot que l'autocorrection mettra à l'espace ou à la ponctuation. */
        Autocorrection,

        /** Mot suivant probable, rien n'étant encore tapé. */
        Prediction,
    }
}

/**
 * Contenu de la barre (apps/clavier/docs/spec-v1.md §7.1) dans l'ordre d'affichage : gauche,
 * centre, droite ; la meilleure proposition au centre. [forTypedWord] : propositions pour un mot en
 * cours de frappe (sinon, prédictions ou rien, et la puce « Coller » peut prendre la place).
 */
data class SuggestionBar(val slots: List<SuggestionItem?> = emptyList(), val forTypedWord: Boolean = false)

/**
 * Bandeau au-dessus des touches, comme la barre d'outils de Gboard (`height: 48px; padding: 0px
 * 8px; gap: 6px` dans la maquette) : bouton de l'historique du presse-papiers (36 × 34 px),
 * séparateur de 2 × 24 px, puis les suggestions, ou la puce « Coller » juste après une copie (§9)
 * tant qu'aucun mot n'est en cours.
 *
 * [suggestions] est lu ici seulement : une frappe ne recompose que ce bandeau (§5.4).
 */
@Composable
fun SuggestionStrip(
    clipboardOpen: Boolean,
    pasteChip: ClipboardItem?,
    suggestions: () -> SuggestionBar,
    onClipboard: () -> Unit,
    actions: KeyboardActions,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(StripHeight)
            .padding(horizontal = StripHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(StripGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeoKey(
            modifier = Modifier.width(ClipboardButtonWidth).height(ChipHeight),
            tone = if (clipboardOpen) NeoTone.Selected else NeoTone.Muted,
            onClick = onClipboard,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_key_clipboard),
                contentDescription = stringResource(R.string.keyboard_clipboard_history),
                modifier = Modifier.size(ClipboardIconSize),
            )
        }
        Box(
            modifier = Modifier
                .width(BorderThin)
                .height(StripDividerHeight)
                .background(NeoTheme.palette.outline),
        )
        val bar = suggestions()
        if (pasteChip != null && !bar.forTypedWord) {
            PasteChip(pasteChip, actions)
        } else {
            bar.slots.forEach { item ->
                if (item == null) Box(Modifier.weight(1f)) else SuggestionChip(item, actions)
            }
        }
    }
}

@Composable
private fun RowScope.SuggestionChip(item: SuggestionItem, actions: KeyboardActions) {
    val isAutocorrection = item.kind == SuggestionItem.Kind.Autocorrection
    NeoKey(
        modifier = Modifier.weight(1f).height(ChipHeight),
        // Sélection (Sky) et gras : jamais la couleur seule (§5.2).
        tone = if (isAutocorrection) NeoTone.Selected else NeoTone.Surface,
        onClick = { actions.pickSuggestion(item) },
    ) {
        Text(
            text = if (item.kind == SuggestionItem.Kind.Typed) "« ${item.text} »" else item.text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isAutocorrection) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun RowScope.PasteChip(item: ClipboardItem, actions: KeyboardActions) {
    NeoKey(
        modifier = Modifier.weight(1f, fill = false).height(ChipHeight),
        tone = NeoTone.Primary,
        onClick = { actions.pasteClip(item.text) },
        onLongClick = actions::dismissPasteChip,
    ) {
        Text(
            stringResource(R.string.clipboard_paste_chip, item.text.replace('\n', ' ')),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

private val StripHeight = 48.dp
private val StripHorizontalPadding = 8.dp
private val StripGap = 6.dp
private val ClipboardButtonWidth = 36.dp
private val ChipHeight = 34.dp
private val ClipboardIconSize = 18.dp
private val StripDividerHeight = 24.dp
