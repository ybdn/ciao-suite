package dev.ybdn.ciaocloud.presentation.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.presentation.ciaoCloudViewModel
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
    onNavigateToSettings: () -> Unit,
) {
    val viewModel = ciaoCloudViewModel { container, app -> HomeViewModel(container, app) }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    // Dernier accès médias constaté ; un scan n'est lancé qu'avec l'accès complet, sinon il
    // ne remonterait qu'une partie de la galerie sans le signaler.
    var mediaAccess by rememberSaveable { mutableStateOf<MediaAccess?>(null) }

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

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.home_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = uiState.destinationUri?.toString()
                    ?: stringResource(R.string.home_no_destination),
            )

            OutlinedButton(onClick = { selectFolderLauncher.launch(null) }) {
                Text(stringResource(R.string.home_select_destination))
            }

            Button(
                onClick = {
                    mediaAccess = context.mediaAccess()
                    if (mediaAccess == MediaAccess.FULL) {
                        viewModel.scanMedia()
                    } else {
                        mediaPermissionLauncher.launch(MEDIA_PERMISSIONS)
                    }
                },
                enabled = !uiState.isScanning,
            ) {
                Text(stringResource(R.string.home_scan_button))
            }

            val mediaAccessMessage = when (mediaAccess) {
                MediaAccess.PARTIAL -> R.string.home_media_permission_partial
                MediaAccess.DENIED -> R.string.home_media_permission_denied
                MediaAccess.FULL, null -> null
            }
            if (mediaAccessMessage != null) {
                Text(
                    text = stringResource(mediaAccessMessage),
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = context::openAppSettings) {
                    Text(stringResource(R.string.home_open_app_settings))
                }
            }

            if (uiState.isScanning) {
                CircularProgressIndicator()
            }

            if (uiState.scannedFiles.isNotEmpty()) {
                Text(
                    stringResource(
                        R.string.home_scan_summary,
                        uiState.photoCount,
                        uiState.videoCount,
                        formatBytes(uiState.totalSizeBytes),
                    ),
                )
            }

            Button(
                onClick = {
                    if (context.hasPermissions(NOTIFICATION_PERMISSION)) {
                        onNavigateToTransfer()
                    } else {
                        notificationPermissionLauncher.launch(NOTIFICATION_PERMISSION)
                    }
                },
                enabled = uiState.canTransfer,
            ) {
                Text(stringResource(R.string.home_transfer_button))
            }

            OutlinedButton(onClick = onNavigateToSettings) {
                Text(stringResource(R.string.settings_title))
            }
        }
    }
}
