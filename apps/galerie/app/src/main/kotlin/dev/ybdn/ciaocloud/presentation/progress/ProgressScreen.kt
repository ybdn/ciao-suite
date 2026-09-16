package dev.ybdn.ciaocloud.presentation.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.TransferAbortReason
import dev.ybdn.ciaocloud.presentation.ciaoCloudViewModel
import dev.ybdn.ciaocloud.presentation.components.NeoButton
import dev.ybdn.ciaocloud.presentation.components.NeoCard
import dev.ybdn.ciaocloud.presentation.components.NeoNotice
import dev.ybdn.ciaocloud.presentation.components.NeoProgressBar
import dev.ybdn.ciaocloud.presentation.components.NeoScreen
import dev.ybdn.ciaocloud.presentation.components.NeoStat
import dev.ybdn.ciaocloud.presentation.components.NeoTag
import dev.ybdn.ciaocloud.presentation.components.NeoTone
import dev.ybdn.ciaocloud.presentation.theme.Teal
import dev.ybdn.ciaocloud.presentation.util.formatBytes

@Composable
fun ProgressScreen(
    onNavigateToDeleteConfirm: () -> Unit,
    onBackToHome: () -> Unit,
) {
    val viewModel = ciaoCloudViewModel { container, app -> ProgressViewModel(container, app) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NeoScreen(
        title = stringResource(if (uiState.isCompleted) R.string.progress_title_done else R.string.progress_title),
    ) {
        val progressFraction = if (uiState.totalFiles > 0) uiState.filesDone / uiState.totalFiles.toFloat() else 0f
        NeoCard {
            Column {
                Text(
                    stringResource(R.string.progress_files_count, uiState.filesDone, uiState.totalFiles),
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(stringResource(R.string.progress_files_caption), style = MaterialTheme.typography.bodyLarge)
            }
            NeoProgressBar(progress = progressFraction)
            NeoTag(stringResource(R.string.progress_bytes_done, formatBytes(uiState.bytesTransferred)), tone = NeoTone.Lime)
        }

        if (uiState.isRunning) {
            uiState.currentFileName?.let { name ->
                NeoCard(tone = NeoTone.Muted) {
                    NeoTag(stringResource(R.string.progress_current_file_label), tone = NeoTone.Surface)
                    Text(name, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    if (uiState.currentFileSizeBytes > 0) {
                        NeoProgressBar(
                            progress = uiState.currentFileBytesCopied.toFloat() / uiState.currentFileSizeBytes,
                            color = Teal,
                            height = 18.dp,
                        )
                        Text(
                            stringResource(
                                R.string.progress_current_file_bytes,
                                formatBytes(uiState.currentFileBytesCopied),
                                formatBytes(uiState.currentFileSizeBytes),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            NeoNotice(stringResource(R.string.progress_background_hint), tone = NeoTone.Surface)
        }

        if (uiState.lastError != null) {
            NeoNotice(
                stringResource(R.string.progress_error, uiState.lastErrorFileName.orEmpty(), uiState.lastError.orEmpty()),
                tone = NeoTone.Coral,
            )
        }

        if (uiState.isCompleted) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeoStat(
                    value = uiState.succeeded.toString(),
                    caption = stringResource(R.string.progress_stat_succeeded),
                    tone = NeoTone.Lime,
                    modifier = Modifier.weight(1f),
                )
                NeoStat(
                    value = uiState.failed.toString(),
                    caption = stringResource(R.string.progress_stat_failed),
                    tone = if (uiState.failed > 0) NeoTone.Brick else NeoTone.Surface,
                    modifier = Modifier.weight(1f),
                )
            }

            val abortMessage = when (val reason = uiState.abortReason) {
                TransferAbortReason.DestinationUnavailable -> stringResource(R.string.transfer_abort_destination_unavailable)
                is TransferAbortReason.InsufficientSpace -> stringResource(
                    R.string.transfer_abort_insufficient_space,
                    formatBytes(reason.requiredBytes),
                    formatBytes(reason.availableBytes),
                )
                null -> uiState.fatalError?.let { stringResource(R.string.transfer_fatal_error, it) }
            }
            abortMessage?.let { NeoNotice(it, tone = NeoTone.Coral) }

            NeoButton(text = stringResource(R.string.delete_confirm_title), onClick = onNavigateToDeleteConfirm)
            NeoButton(
                text = stringResource(R.string.progress_back_home),
                onClick = onBackToHome,
                tone = NeoTone.Surface,
            )
        }
    }
}
