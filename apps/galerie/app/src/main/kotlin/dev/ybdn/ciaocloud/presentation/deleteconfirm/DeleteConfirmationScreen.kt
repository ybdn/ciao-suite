package dev.ybdn.ciaocloud.presentation.deleteconfirm

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.presentation.ciaoCloudViewModel
import dev.ybdn.ciaocloud.presentation.util.formatBytes

@Composable
fun DeleteConfirmationScreen(
    onCancel: () -> Unit,
    onDeleted: () -> Unit,
) {
    val viewModel = ciaoCloudViewModel { container, app -> DeleteConfirmationViewModel(container, app) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.verifiedCount == 0 -> Text(stringResource(R.string.delete_confirm_nothing))
                else -> {
                    Text(
                        stringResource(
                            R.string.delete_confirm_summary,
                            uiState.verifiedCount,
                            formatBytes(uiState.freeableBytes),
                        ),
                    )
                    Text(stringResource(R.string.delete_confirm_explanation))
                }
            }

            uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = viewModel::confirmDeletion, enabled = uiState.canDelete) {
                Text(stringResource(R.string.delete_confirm_button))
            }

            if (uiState.isDeleting) CircularProgressIndicator()

            OutlinedButton(onClick = onCancel, enabled = !uiState.isDeleting) {
                Text(stringResource(R.string.delete_confirm_cancel))
            }
        }
    }
}
