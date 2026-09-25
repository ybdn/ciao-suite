package dev.ybdn.ciaocloud.presentation.deleteconfirm

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.presentation.ciaoCloudViewModel
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoNotice
import dev.ybdn.ciao.designsystem.components.NeoScreen
import dev.ybdn.ciao.designsystem.components.NeoStat
import dev.ybdn.ciao.designsystem.components.NeoTone
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

    NeoScreen(title = stringResource(R.string.delete_confirm_title)) {
        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.verifiedCount == 0 -> NeoNotice(stringResource(R.string.delete_confirm_nothing), tone = NeoTone.Muted)
            else -> {
                NeoStat(
                    value = formatBytes(uiState.freeableBytes),
                    caption = stringResource(R.string.delete_confirm_stat_freeable),
                    tone = NeoTone.Success,
                    modifier = Modifier.fillMaxWidth(),
                )
                NeoStat(
                    value = uiState.verifiedCount.toString(),
                    caption = stringResource(R.string.delete_confirm_stat_verified),
                    tone = NeoTone.Info,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.delete_confirm_explanation), style = MaterialTheme.typography.bodyLarge)
            }
        }

        uiState.errorMessage?.let { NeoNotice(it, tone = NeoTone.Danger) }

        NeoButton(
            text = stringResource(R.string.delete_confirm_button),
            onClick = viewModel::confirmDeletion,
            enabled = uiState.canDelete,
            tone = NeoTone.Danger,
        )

        if (uiState.isDeleting) CircularProgressIndicator()

        NeoButton(
            text = stringResource(R.string.delete_confirm_cancel),
            onClick = onCancel,
            tone = NeoTone.Surface,
            enabled = !uiState.isDeleting,
        )
    }
}
