package dev.ybdn.ciaocloud.presentation.progress

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.presentation.ciaoCloudViewModel
import dev.ybdn.ciaocloud.presentation.util.formatBytes

@Composable
fun ProgressScreen(onTransferCompleted: () -> Unit) {
    val viewModel = ciaoCloudViewModel { container, app -> ProgressViewModel(container, app) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.startTransfer() }
    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) onTransferCompleted()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.progress_title)) }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            val progressFraction = if (uiState.totalFiles > 0) {
                uiState.filesDone / uiState.totalFiles.toFloat()
            } else {
                0f
            }
            LinearProgressIndicator(progress = { progressFraction }, modifier = Modifier.fillMaxWidth())

            uiState.currentFileName?.let {
                Text(stringResource(R.string.progress_current_file, it))
            }
            Text(stringResource(R.string.progress_files_done, uiState.filesDone, uiState.totalFiles))
            Text(stringResource(R.string.progress_bytes_done, formatBytes(uiState.bytesTransferred)))

            uiState.lastError?.let { error ->
                Text(stringResource(R.string.progress_error, uiState.currentFileName ?: "", error))
            }

            if (uiState.isCompleted) {
                Button(onClick = onTransferCompleted) {
                    Text(stringResource(R.string.delete_confirm_title))
                }
            }
        }
    }
}
