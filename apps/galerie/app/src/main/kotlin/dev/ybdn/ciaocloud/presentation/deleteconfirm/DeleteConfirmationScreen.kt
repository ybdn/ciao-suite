package dev.ybdn.ciaocloud.presentation.deleteconfirm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
fun DeleteConfirmationScreen(
    onCancel: () -> Unit,
    onDeleted: () -> Unit,
) {
    val viewModel = ciaoCloudViewModel { container, app -> DeleteConfirmationViewModel(container, app) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) onDeleted()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.delete_confirm_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(
                    R.string.delete_confirm_summary,
                    uiState.verifiedCount,
                    formatBytes(uiState.freeableBytes),
                ),
            )

            uiState.errorMessage?.let { Text(it) }

            Button(onClick = viewModel::confirmDeletion, enabled = !uiState.isDeleting) {
                Text(stringResource(R.string.delete_confirm_button))
            }

            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.delete_confirm_cancel))
            }
        }
    }
}
