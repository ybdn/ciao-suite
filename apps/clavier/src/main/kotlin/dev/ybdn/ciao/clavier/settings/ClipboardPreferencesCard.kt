package dev.ybdn.ciao.clavier.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciao.clavier.R
import dev.ybdn.ciao.clavier.data.clipboard.ClipboardPreferences
import dev.ybdn.ciao.clavier.domain.clipboard.ClipboardRetention
import dev.ybdn.ciao.clavier.domain.clipboard.ClipboardSettings
import dev.ybdn.ciao.designsystem.components.NeoCard
import dev.ybdn.ciao.designsystem.components.NeoSectionHeader
import dev.ybdn.ciao.designsystem.components.NeoSegmentedChoice
import dev.ybdn.ciao.designsystem.components.NeoSwitchRow
import kotlinx.coroutines.launch

/** Réglages de l'historique du presse-papiers (apps/clavier/docs/spec-v1.md §9). */
@Composable
fun ClipboardPreferencesCard(index: String) {
    val context = LocalContext.current
    val preferences = remember { ClipboardPreferences(context) }
    val settings by preferences.settings.collectAsStateWithLifecycle(initialValue = ClipboardSettings())
    val scope = rememberCoroutineScope()

    NeoCard {
        NeoSectionHeader(index, stringResource(R.string.preferences_clipboard_title))
        NeoSwitchRow(
            label = stringResource(R.string.preferences_clipboard_history),
            checked = settings.historyEnabled,
            onCheckedChange = { scope.launch { preferences.setHistoryEnabled(it) } },
        )
        if (settings.historyEnabled) {
            Text(stringResource(R.string.preferences_clipboard_retention), style = MaterialTheme.typography.titleMedium)
            NeoSegmentedChoice(
                options = listOf(
                    stringResource(R.string.preferences_clipboard_retention_hour),
                    stringResource(R.string.preferences_clipboard_retention_day),
                    stringResource(R.string.preferences_clipboard_retention_week),
                ),
                selectedIndex = settings.retention.ordinal,
                onSelect = { scope.launch { preferences.setRetention(ClipboardRetention.entries[it]) } },
            )
        }
    }
}
