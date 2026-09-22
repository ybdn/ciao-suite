package dev.ybdn.ciaocloud.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.ThemeMode
import dev.ybdn.ciaocloud.presentation.ciaoCloudViewModel
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoCard
import dev.ybdn.ciao.designsystem.components.NeoScreen
import dev.ybdn.ciao.designsystem.components.NeoSegmentedChoice
import dev.ybdn.ciao.designsystem.components.NeoSwitchRow
import dev.ybdn.ciao.designsystem.components.NeoTag
import dev.ybdn.ciao.designsystem.components.NeoTone
import dev.ybdn.ciaocloud.presentation.gallery.SsdIndexControls
import dev.ybdn.ciaocloud.presentation.util.formatBytes

@Composable
fun SettingsScreen() {
    val viewModel = ciaoCloudViewModel { container, app -> SettingsViewModel(container, app) }
    val destinationUri by viewModel.destinationUri.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val ssdIndexState by viewModel.ssdIndexState.collectAsState()
    val cacheMaxBytes by viewModel.thumbnailCacheMaxBytes.collectAsState()
    val cacheUsedBytes by viewModel.thumbnailCacheUsedBytes.collectAsState()
    val shareStripMetadata by viewModel.shareStripMetadata.collectAsState()
    var showTriageResetDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    if (showTriageResetDialog) {
        TriageResetDialog(
            onConfirm = {
                showTriageResetDialog = false
                viewModel.resetTriage {
                    Toast.makeText(context, context.getString(R.string.settings_triage_reset_done), Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showTriageResetDialog = false },
        )
    }

    val selectFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::onDestinationSelected) }

    NeoScreen(title = stringResource(R.string.settings_title)) {
        PermissionsCard(
            destinationUri = destinationUri,
            onSelectDestination = { selectFolderLauncher.launch(null) },
        )

        NeoCard {
            NeoTag(stringResource(R.string.settings_destination_label), tone = NeoTone.Yellow)
            Text(
                text = destinationUri?.toString() ?: stringResource(R.string.home_no_destination),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            NeoButton(
                text = stringResource(R.string.settings_change_destination),
                onClick = { selectFolderLauncher.launch(null) },
                tone = NeoTone.Surface,
            )
        }

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

        NeoCard {
            NeoTag(stringResource(R.string.settings_privacy_label), tone = NeoTone.Pink)
            NeoSwitchRow(
                label = stringResource(R.string.settings_share_strip_metadata),
                checked = shareStripMetadata,
                onCheckedChange = viewModel::onShareStripMetadataChanged,
            )
            Text(stringResource(R.string.settings_share_strip_metadata_hint), style = MaterialTheme.typography.bodySmall)
        }

        NeoCard {
            NeoTag(stringResource(R.string.settings_gallery_label), tone = NeoTone.Lavender)
            Text(stringResource(R.string.settings_ssd_index_hint), style = MaterialTheme.typography.bodyMedium)
            SsdIndexControls(state = ssdIndexState, onRefresh = viewModel::refreshSsdIndex)
        }

        NeoCard {
            NeoTag(stringResource(R.string.settings_triage_label), tone = NeoTone.Yellow)
            Text(stringResource(R.string.settings_triage_hint), style = MaterialTheme.typography.bodyMedium)
            NeoButton(
                text = stringResource(R.string.settings_triage_reset),
                onClick = { showTriageResetDialog = true },
                tone = NeoTone.Surface,
            )
        }

        NeoCard {
            NeoTag(stringResource(R.string.settings_thumbnail_cache_label), tone = NeoTone.Green)
            cacheUsedBytes?.let { used ->
                Text(
                    stringResource(R.string.settings_thumbnail_cache_usage, formatBytes(used), formatBytes(cacheMaxBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val sizes = THUMBNAIL_CACHE_SIZES
            NeoSegmentedChoice(
                options = sizes.map(::formatCacheSize),
                selectedIndex = sizes.indexOf(cacheMaxBytes),
                onSelect = { viewModel.onThumbnailCacheMaxBytesSelected(sizes[it]) },
            )
            Text(stringResource(R.string.settings_thumbnail_cache_hint), style = MaterialTheme.typography.bodySmall)
            NeoButton(
                text = stringResource(R.string.settings_thumbnail_cache_clear),
                onClick = viewModel::clearThumbnailCache,
                tone = NeoTone.Surface,
            )
        }
    }
}

/** Action large (tout l'historique de tri), confirmée avant exécution. */
@Composable
private fun TriageResetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        NeoCard(modifier = Modifier.padding(8.dp)) {
            Text(stringResource(R.string.settings_triage_reset_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.settings_triage_reset_message), style = MaterialTheme.typography.bodyMedium)
            NeoButton(stringResource(R.string.settings_triage_reset_confirm), onClick = onConfirm, tone = NeoTone.Pink)
            NeoButton(stringResource(R.string.delete_confirm_cancel), onClick = onDismiss, tone = NeoTone.Surface)
        }
    }
}

private const val MB = 1024L * 1024

private val THUMBNAIL_CACHE_SIZES = listOf(250 * MB, 500 * MB, 1024 * MB, 2048 * MB)

/** « 250 Mo », « 1 Go » : libellés courts pour le choix segmenté. */
private fun formatCacheSize(bytes: Long): String =
    if (bytes >= 1024 * MB) "${bytes / (1024 * MB)} Go" else "${bytes / MB} Mo"
