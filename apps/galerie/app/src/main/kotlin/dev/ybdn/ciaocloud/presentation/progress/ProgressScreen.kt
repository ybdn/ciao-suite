package dev.ybdn.ciaocloud.presentation.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.TransferAbortReason
import dev.ybdn.ciaocloud.presentation.ciaoCloudViewModel
import dev.ybdn.ciaocloud.presentation.util.formatBytes

@Composable
fun ProgressScreen(
    onNavigateToDeleteConfirm: () -> Unit,
    onBackToHome: () -> Unit,
) {
    val viewModel = ciaoCloudViewModel { container, app -> ProgressViewModel(container, app) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isCompleted) R.string.progress_title_done else R.string.progress_title,
                        ),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val progressFraction = if (uiState.totalFiles > 0) uiState.filesDone / uiState.totalFiles.toFloat() else 0f
            LinearProgressIndicator(progress = { progressFraction }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.progress_files_done, uiState.filesDone, uiState.totalFiles))
            Text(stringResource(R.string.progress_bytes_done, formatBytes(uiState.bytesTransferred)))

            if (uiState.isRunning) {
                uiState.currentFileName?.let { name ->
                    Text(stringResource(R.string.progress_current_file, name))
                    if (uiState.currentFileSizeBytes > 0) {
                        LinearProgressIndicator(
                            progress = { (uiState.currentFileBytesCopied.toFloat() / uiState.currentFileSizeBytes).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(
                                R.string.progress_current_file_bytes,
                                formatBytes(uiState.currentFileBytesCopied),
                                formatBytes(uiState.currentFileSizeBytes),
                            ),
                        )
                    }
                }
                Text(stringResource(R.string.progress_background_hint))
            }

            if (uiState.lastError != null) {
                Text(
                    stringResource(R.string.progress_error, uiState.lastErrorFileName.orEmpty(), uiState.lastError.orEmpty()),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (uiState.isCompleted) {
                Text(stringResource(R.string.transfer_summary, uiState.succeeded, uiState.failed))

                val abortMessage = when (val reason = uiState.abortReason) {
                    TransferAbortReason.DestinationUnavailable -> stringResource(R.string.transfer_abort_destination_unavailable)
                    is TransferAbortReason.InsufficientSpace -> stringResource(
                        R.string.transfer_abort_insufficient_space,
                        formatBytes(reason.requiredBytes),
                        formatBytes(reason.availableBytes),
                    )
                    null -> uiState.fatalError?.let { stringResource(R.string.transfer_fatal_error, it) }
                }
                abortMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Button(onClick = onNavigateToDeleteConfirm) {
                    Text(stringResource(R.string.delete_confirm_title))
                }
                OutlinedButton(onClick = onBackToHome) {
                    Text(stringResource(R.string.progress_back_home))
                }
            }
        }
    }
}
