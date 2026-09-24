package dev.ybdn.ciao.clavier.ime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ybdn.ciao.clavier.R
import dev.ybdn.ciao.clavier.domain.clipboard.ClipboardItem
import dev.ybdn.ciao.designsystem.components.BorderWidth
import dev.ybdn.ciao.designsystem.components.NeoKey
import dev.ybdn.ciao.designsystem.components.NeoTone
import dev.ybdn.ciao.designsystem.components.ShadowMedium
import dev.ybdn.ciao.designsystem.components.neoSurface
import dev.ybdn.ciao.designsystem.theme.NeoTheme

/** Données du presse-papiers, fournies par le service. */
@Immutable
data class ClipboardPanelData(
    /** Éléments à afficher, dans l'ordre (épinglés d'abord, puis du plus récent au plus ancien). */
    val items: List<ClipboardItem> = emptyList(),
    val historyEnabled: Boolean = true,
    /** Dernière copie de moins d'une minute, proposée dans la puce « Coller ». */
    val pasteChip: ClipboardItem? = null,
)

private val CardActionSize = 36.dp
private val CardActionIconSize = 18.dp
private val BottomRowHeight = 48.dp

/**
 * Historique du presse-papiers (apps/clavier/docs/spec-v1.md §9), à la place des touches et à la
 * même hauteur ([height]) : une carte par texte copié (appui = coller), avec épingler et supprimer.
 */
@Composable
internal fun ClipboardPanel(
    height: Dp,
    data: ClipboardPanelData,
    container: KeyboardContainer,
    actions: KeyboardActions,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().height(height)) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val message = when {
                !data.historyEnabled -> R.string.clipboard_disabled
                data.items.isEmpty() -> R.string.clipboard_empty
                else -> null
            }
            if (message != null) {
                Text(
                    stringResource(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeoTheme.palette.content,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Place pour l'ombre moyenne des cartes, à droite et en bas.
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp, end = ShadowMedium),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(data.items, key = { it.id }) { item ->
                        ClipboardCard(item, actions)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(BottomRowHeight).padding(top = 4.dp, end = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
            Spacer(modifier = Modifier.weight(7f))
            BackspaceKey(weight = 1.5f, container = container, actions = actions)
        }
    }
}

/** Carte (bordure 3 dp, ombre moyenne, spec §5.1) : le texte, puis épingler et supprimer. */
@Composable
private fun ClipboardCard(item: ClipboardItem, actions: KeyboardActions) {
    val palette = NeoTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .neoSurface(color = palette.surface, outline = palette.outline, shadowOffset = ShadowMedium, borderWidth = BorderWidth),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.text,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.content,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = stringResource(R.string.clipboard_paste)) { actions.pasteClip(item.text) }
                .padding(12.dp),
        )
        NeoKey(
            onClick = { actions.setClipPinned(item.id, !item.pinned) },
            modifier = Modifier.size(CardActionSize),
            tone = if (item.pinned) NeoTone.Selected else NeoTone.Muted,
        ) {
            Icon(
                painter = painterResource(if (item.pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin),
                contentDescription = stringResource(if (item.pinned) R.string.clipboard_unpin else R.string.clipboard_pin),
                modifier = Modifier.size(CardActionIconSize),
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        NeoKey(
            onClick = { actions.deleteClip(item.id) },
            modifier = Modifier.size(CardActionSize),
            tone = NeoTone.Muted,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.clipboard_delete),
                modifier = Modifier.size(CardActionIconSize),
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
    }
}
