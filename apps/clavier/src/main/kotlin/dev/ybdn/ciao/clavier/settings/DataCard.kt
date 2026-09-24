package dev.ybdn.ciao.clavier.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciao.clavier.R
import dev.ybdn.ciao.clavier.data.EmojiPreferences
import dev.ybdn.ciao.clavier.data.clipboard.ClipboardHistory
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoCard
import dev.ybdn.ciao.designsystem.components.NeoSectionHeader
import dev.ybdn.ciao.designsystem.components.NeoTone
import kotlinx.coroutines.launch

/**
 * Données mémorisées par le clavier (apps/clavier/docs/spec-v1.md §3.4, §10.3) : chacune
 * s'efface en un geste (épinglés compris pour le presse-papiers). Le dictionnaire personnel
 * rejoindra cette carte au lot 5.
 */
@Composable
fun DataCard(index: String) {
    val context = LocalContext.current
    val emojis = remember { EmojiPreferences(context) }
    val recents by emojis.recents.collectAsStateWithLifecycle(initialValue = emptyList())
    val clipboard = remember { ClipboardHistory(context) }
    val clips by clipboard.items.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    NeoCard {
        NeoSectionHeader(index, stringResource(R.string.data_title))
        NeoButton(
            text = stringResource(R.string.data_clear_recent_emojis),
            tone = NeoTone.Danger,
            enabled = recents.isNotEmpty(),
            onClick = { scope.launch { emojis.clearRecents() } },
        )
        NeoButton(
            text = stringResource(R.string.data_clear_clipboard),
            tone = NeoTone.Danger,
            enabled = clips.isNotEmpty(),
            onClick = { scope.launch { clipboard.clear() } },
        )
    }
}
