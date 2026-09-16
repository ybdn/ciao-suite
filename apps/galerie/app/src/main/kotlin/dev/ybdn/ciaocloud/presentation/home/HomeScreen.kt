package dev.ybdn.ciaocloud.presentation.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.presentation.ciaoCloudViewModel
import dev.ybdn.ciaocloud.presentation.util.formatBytes

@Composable
fun HomeScreen(
    onNavigateToTransfer: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val viewModel = ciaoCloudViewModel { container, app -> HomeViewModel(container, app) }
    val uiState by viewModel.uiState.collectAsState()

    val selectFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::onDestinationSelected) }

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

            Button(onClick = viewModel::scanMedia, enabled = !uiState.isScanning) {
                Text(stringResource(R.string.home_scan_button))
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

            Button(onClick = onNavigateToTransfer, enabled = uiState.canTransfer) {
                Text(stringResource(R.string.home_transfer_button))
            }

            OutlinedButton(onClick = onNavigateToSettings) {
                Text(stringResource(R.string.settings_title))
            }
        }
    }
}
