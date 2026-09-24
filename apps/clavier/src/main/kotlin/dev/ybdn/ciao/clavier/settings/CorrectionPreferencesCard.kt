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
import dev.ybdn.ciao.clavier.data.TypingPreferences
import dev.ybdn.ciao.clavier.domain.input.TypingSettings
import dev.ybdn.ciao.designsystem.components.NeoCard
import dev.ybdn.ciao.designsystem.components.NeoSectionHeader
import dev.ybdn.ciao.designsystem.components.NeoSwitchRow
import kotlinx.coroutines.launch

/**
 * Suggestions et autocorrection (apps/clavier/docs/spec-v1.md §7.1, §10.2), avec l'attribution
 * du dictionnaire qu'exige sa licence (§7.2, CC BY 4.0).
 */
@Composable
fun CorrectionPreferencesCard(index: String) {
    val context = LocalContext.current
    val preferences = remember { TypingPreferences(context) }
    val settings by preferences.settings.collectAsStateWithLifecycle(initialValue = TypingSettings())
    val scope = rememberCoroutineScope()

    NeoCard {
        NeoSectionHeader(index, stringResource(R.string.preferences_correction_title))
        NeoSwitchRow(
            label = stringResource(R.string.preferences_suggestions),
            checked = settings.suggestions,
            onCheckedChange = { scope.launch { preferences.setSuggestions(it) } },
        )
        // L'autocorrection applique la meilleure suggestion : sans suggestions, rien à corriger.
        if (settings.suggestions) {
            NeoSwitchRow(
                label = stringResource(R.string.preferences_autocorrect),
                checked = settings.autocorrect,
                onCheckedChange = { scope.launch { preferences.setAutocorrect(it) } },
            )
        }
        Text(stringResource(R.string.preferences_dictionary_attribution), style = MaterialTheme.typography.bodySmall)
    }
}
