package dev.ybdn.ciaocloud.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.ThemeMode
import dev.ybdn.ciaocloud.presentation.ciaoCloudViewModel
import dev.ybdn.ciaocloud.presentation.components.NeoButton
import dev.ybdn.ciaocloud.presentation.components.NeoCard
import dev.ybdn.ciaocloud.presentation.components.NeoScreen
import dev.ybdn.ciaocloud.presentation.components.NeoSegmentedChoice
import dev.ybdn.ciaocloud.presentation.components.NeoTag
import dev.ybdn.ciaocloud.presentation.components.NeoTone

@Composable
fun SettingsScreen() {
    val viewModel = ciaoCloudViewModel { container, app -> SettingsViewModel(container, app) }
    val destinationUri by viewModel.destinationUri.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    val selectFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::onDestinationSelected) }

    NeoScreen(title = stringResource(R.string.settings_title)) {
        NeoCard {
            NeoTag(stringResource(R.string.settings_destination_label), tone = NeoTone.Yellow)
            Text(
                text = destinationUri?.toString() ?: stringResource(R.string.home_no_destination),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
        }

        NeoButton(
            text = stringResource(R.string.settings_change_destination),
            onClick = { selectFolderLauncher.launch(null) },
            tone = NeoTone.Surface,
        )

        NeoCard {
            NeoTag(stringResource(R.string.settings_appearance_label), tone = NeoTone.Sky)
            val modes = ThemeMode.entries
            NeoSegmentedChoice(
                options = modes.map { mode ->
                    stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.settings_theme_system
                            ThemeMode.LIGHT -> R.string.settings_theme_light
                            ThemeMode.DARK -> R.string.settings_theme_dark
                        },
                    )
                },
                selectedIndex = modes.indexOf(themeMode),
                onSelect = { viewModel.onThemeModeSelected(modes[it]) },
            )
        }
    }
}
