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
import dev.ybdn.ciao.clavier.data.AppearancePreferences
import dev.ybdn.ciao.clavier.domain.appearance.AppearanceSettings
import dev.ybdn.ciao.clavier.domain.appearance.KeyboardHeight
import dev.ybdn.ciao.clavier.domain.appearance.ThemeMode
import dev.ybdn.ciao.designsystem.components.NeoCard
import dev.ybdn.ciao.designsystem.components.NeoSectionHeader
import dev.ybdn.ciao.designsystem.components.NeoSegmentedChoice
import kotlinx.coroutines.launch

/**
 * Apparence (apps/clavier/docs/spec-v1.md §5.3, §10.2) : thème (système, clair, sombre), appliqué
 * au clavier et à cette app, et hauteur du clavier en trois tailles.
 */
@Composable
fun AppearancePreferencesCard(index: String) {
    val context = LocalContext.current
    val preferences = remember { AppearancePreferences(context) }
    val settings by preferences.settings.collectAsStateWithLifecycle(initialValue = AppearanceSettings())
    val scope = rememberCoroutineScope()

    NeoCard {
        NeoSectionHeader(index, stringResource(R.string.preferences_appearance_title))

        Text(stringResource(R.string.preferences_theme), style = MaterialTheme.typography.titleMedium)
        NeoSegmentedChoice(
            options = listOf(
                stringResource(R.string.preferences_theme_system),
                stringResource(R.string.preferences_theme_light),
                stringResource(R.string.preferences_theme_dark),
            ),
            selectedIndex = settings.themeMode.ordinal,
            onSelect = { scope.launch { preferences.setThemeMode(ThemeMode.entries[it]) } },
        )

        Text(stringResource(R.string.preferences_keyboard_height), style = MaterialTheme.typography.titleMedium)
        NeoSegmentedChoice(
            options = listOf(
                stringResource(R.string.preferences_keyboard_height_compact),
                stringResource(R.string.preferences_keyboard_height_standard),
                stringResource(R.string.preferences_keyboard_height_tall),
            ),
            selectedIndex = settings.keyboardHeight.ordinal,
            onSelect = { scope.launch { preferences.setKeyboardHeight(KeyboardHeight.entries[it]) } },
        )
    }
}
