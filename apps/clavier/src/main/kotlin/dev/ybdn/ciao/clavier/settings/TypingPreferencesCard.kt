package dev.ybdn.ciao.clavier.settings

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
 * Réglages de frappe (apps/clavier/docs/spec-v1.md §6.3, §10.2). Le clavier les applique dès le
 * prochain caractère, sans redémarrage : il suit le même DataStore.
 */
@Composable
fun TypingPreferencesCard(index: String) {
    val context = LocalContext.current
    val preferences = remember { TypingPreferences(context) }
    val settings by preferences.settings.collectAsStateWithLifecycle(initialValue = TypingSettings())
    val scope = rememberCoroutineScope()

    NeoCard {
        NeoSectionHeader(index, stringResource(R.string.preferences_typing_title))
        NeoSwitchRow(
            label = stringResource(R.string.preferences_auto_capitalize),
            checked = settings.autoCapitalize,
            onCheckedChange = { scope.launch { preferences.setAutoCapitalize(it) } },
        )
        NeoSwitchRow(
            label = stringResource(R.string.preferences_double_space_period),
            checked = settings.doubleSpacePeriod,
            onCheckedChange = { scope.launch { preferences.setDoubleSpacePeriod(it) } },
        )
        NeoSwitchRow(
            label = stringResource(R.string.preferences_non_breaking_space),
            checked = settings.nonBreakingSpace,
            onCheckedChange = { scope.launch { preferences.setNonBreakingSpace(it) } },
        )
    }
}
