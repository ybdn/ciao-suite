package dev.ybdn.ciaocloud.presentation.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.presentation.ciaoCloudViewModel
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoCard
import dev.ybdn.ciao.designsystem.components.NeoMarquee
import dev.ybdn.ciao.designsystem.components.NeoNotice
import dev.ybdn.ciao.designsystem.components.NeoScreen
import dev.ybdn.ciao.designsystem.components.NeoSectionHeader
import dev.ybdn.ciao.designsystem.components.NeoStat
import dev.ybdn.ciao.designsystem.components.NeoTag
import dev.ybdn.ciao.designsystem.components.NeoTone
import dev.ybdn.ciaocloud.presentation.util.MEDIA_PERMISSIONS
import dev.ybdn.ciaocloud.presentation.util.MediaAccess
import dev.ybdn.ciaocloud.presentation.util.NOTIFICATION_PERMISSION
import dev.ybdn.ciaocloud.presentation.util.formatBytes
import dev.ybdn.ciaocloud.presentation.util.hasPermissions
import dev.ybdn.ciaocloud.presentation.util.mediaAccess
import dev.ybdn.ciaocloud.presentation.util.openAppSettings

@Composable
fun HomeScreen(
    onNavigateToTransfer: () -> Unit,
    onNavigateToDeleteConfirm: () -> Unit,
) {
    val viewModel = ciaoCloudViewModel { container, app -> HomeViewModel(container, app) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Dernier accès médias constaté ; un scan n'est lancé qu'avec l'accès complet, sinon il
    // ne remonterait qu'une partie de la galerie sans le signaler.
    var mediaAccess by rememberSaveable { mutableStateOf<MediaAccess?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshDestination()
        onPauseOrDispose { }
    }

    val selectFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::onDestinationSelected) }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        mediaAccess = context.mediaAccess()
        if (mediaAccess == MediaAccess.FULL) viewModel.scanMedia()
    }

    // La notification n'est pas indispensable au transfert (le service tourne sans) :
    // on la demande, mais on navigue quelle que soit la réponse.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { onNavigateToTransfer() }

    NeoScreen(
        title = stringResource(R.string.home_title),
        header = { NeoMarquee(stringResource(R.string.home_marquee)) },
    ) {
        // --- 01 Destination ---
        NeoSectionHeader("01", stringResource(R.string.home_step_destination))
        val status = uiState.destinationStatus
        when {
            uiState.destinationUri == null -> NeoNotice(stringResource(R.string.home_no_destination), tone = NeoTone.Muted)
            status == null -> CircularProgressIndicator()
            !status.isAvailable -> NeoNotice(stringResource(R.string.home_destination_unavailable), tone = NeoTone.Danger)
            else -> NeoCard {
                NeoTag(stringResource(R.string.home_destination_ready, status.name ?: "SSD"), tone = NeoTone.Success)
                status.availableBytes?.let {
                    Column {
                        Text(formatBytes(it), style = MaterialTheme.typography.displayMedium)
                        Text(stringResource(R.string.home_stat_free_space), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        NeoButton(
            text = stringResource(R.string.home_select_destination),
            onClick = { selectFolderLauncher.launch(null) },
            tone = NeoTone.Surface,
        )

        // --- 02 Scan ---
        NeoSectionHeader("02", stringResource(R.string.home_step_scan))
        NeoButton(
            text = stringResource(R.string.home_scan_button),
            onClick = {
                mediaAccess = context.mediaAccess()
                if (mediaAccess == MediaAccess.FULL) {
                    viewModel.scanMedia()
                } else {
                    mediaPermissionLauncher.launch(MEDIA_PERMISSIONS)
                }
            },
            tone = NeoTone.Primary,
            enabled = !uiState.isScanning && !uiState.isTransferRunning,
        )

        val mediaAccessMessage = when (mediaAccess) {
            MediaAccess.PARTIAL -> R.string.home_media_permission_partial
            MediaAccess.DENIED -> R.string.home_media_permission_denied
            MediaAccess.FULL, null -> null
        }
        if (mediaAccessMessage != null) {
            NeoNotice(stringResource(mediaAccessMessage), tone = NeoTone.Warning)
            NeoButton(
                text = stringResource(R.string.home_open_app_settings),
                onClick = context::openAppSettings,
                tone = NeoTone.Surface,
            )
        }

        if (uiState.isScanning) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                Text(stringResource(R.string.home_scanning), style = MaterialTheme.typography.titleMedium)
            }
        }

        uiState.scanError?.let {
            NeoNotice(stringResource(R.string.home_scan_error, it), tone = NeoTone.Danger)
        }

        uiState.scannedFiles?.let { files ->
            if (files.isEmpty()) {
                NeoNotice(stringResource(R.string.home_scan_nothing), tone = NeoTone.Muted)
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(IntrinsicSize.Min),
                ) {
                    NeoStat(
                        value = uiState.photoCount.toString(),
                        caption = stringResource(R.string.home_stat_photos),
                        tone = NeoTone.Lavender,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    NeoStat(
                        value = uiState.videoCount.toString(),
                        caption = stringResource(R.string.home_stat_videos),
                        tone = NeoTone.Sky,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                NeoStat(
                    value = formatBytes(uiState.totalSizeBytes),
                    caption = stringResource(R.string.home_stat_total_size),
                    tone = NeoTone.Success,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!uiState.hasEnoughSpace) {
                    NeoNotice(stringResource(R.string.home_insufficient_space), tone = NeoTone.Danger)
                }
            }
        }

        // --- 03 Transfert ---
        NeoSectionHeader("03", stringResource(R.string.home_step_transfer))
        NeoButton(
            text = stringResource(
                if (uiState.isTransferRunning) R.string.home_transfer_running_button else R.string.home_transfer_button,
            ),
            onClick = {
                if (uiState.isTransferRunning || context.hasPermissions(NOTIFICATION_PERMISSION)) {
                    onNavigateToTransfer()
                } else {
                    notificationPermissionLauncher.launch(NOTIFICATION_PERMISSION)
                }
            },
            enabled = uiState.canTransfer,
            tone = NeoTone.Primary,
        )

        // --- 04 Libérer ---
        NeoSectionHeader("04", stringResource(R.string.home_step_free))
        NeoButton(
            text = stringResource(R.string.delete_confirm_title),
            onClick = onNavigateToDeleteConfirm,
            tone = NeoTone.Surface,
            enabled = !uiState.isTransferRunning,
        )
    }
}
