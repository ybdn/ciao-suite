package dev.ybdn.ciaocloud.presentation.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.EditAvailability
import dev.ybdn.ciaocloud.domain.model.EditCapabilities
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.GalleryLocation
import dev.ybdn.ciaocloud.domain.model.SaveMode
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoCard
import dev.ybdn.ciao.designsystem.components.NeoNotice
import dev.ybdn.ciao.designsystem.components.NeoTone

/**
 * Choix d'enregistrement : copie (action principale) ou remplacement de l'original (secondaire,
 * masqué s'il est sans objet, confirmé car irréversible).
 */
@Composable
fun SaveEditDialog(
    item: GalleryItem,
    capabilities: EditCapabilities,
    onSave: (SaveMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmReplace by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        NeoCard(modifier = Modifier.padding(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!confirmReplace) {
                    Text(stringResource(R.string.editor_save_title), style = MaterialTheme.typography.titleLarge)
                    AvailabilityNotice(capabilities.editCopy)
                    NeoButton(
                        stringResource(R.string.editor_save_copy),
                        onClick = { onSave(SaveMode.COPY) },
                        enabled = capabilities.editCopy.isAvailable,
                        tone = NeoTone.Primary,
                    )
                    val replace = capabilities.replace
                    if (replace !is EditAvailability.Unavailable || !replace.reason.hidesAction) {
                        AvailabilityNotice(replace)
                        NeoButton(
                            stringResource(R.string.editor_replace_original),
                            onClick = { confirmReplace = true },
                            enabled = replace.isAvailable,
                            tone = NeoTone.Surface,
                        )
                    }
                    NeoButton(stringResource(R.string.delete_confirm_cancel), onClick = onDismiss, tone = NeoTone.Surface)
                } else {
                    Text(stringResource(R.string.editor_replace_confirm_title), style = MaterialTheme.typography.titleLarge)
                    NeoNotice(
                        stringResource(
                            if (item.location == GalleryLocation.BOTH) R.string.editor_replace_confirm_both else R.string.editor_replace_confirm,
                        ),
                        tone = NeoTone.Danger,
                    )
                    NeoButton(
                        stringResource(R.string.editor_replace_confirm_button),
                        onClick = { onSave(SaveMode.REPLACE) },
                        tone = NeoTone.Danger,
                    )
                    NeoButton(stringResource(R.string.delete_confirm_cancel), onClick = { confirmReplace = false }, tone = NeoTone.Surface)
                }
            }
        }
    }
}

@Composable
private fun AvailabilityNotice(availability: EditAvailability) {
    val reason = (availability as? EditAvailability.Unavailable)?.reason ?: return
    if (!reason.hidesAction) NeoNotice(stringResource(reason.messageRes()), tone = NeoTone.Warning)
}

@Composable
fun DiscardChangesDialog(onDiscard: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        NeoCard(modifier = Modifier.padding(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.editor_discard_title), style = MaterialTheme.typography.titleLarge)
                NeoButton(stringResource(R.string.editor_discard), onClick = onDiscard, tone = NeoTone.Danger)
                NeoButton(stringResource(R.string.editor_keep_editing), onClick = onDismiss, tone = NeoTone.Surface)
            }
        }
    }
}

/** Indicateur bloquant : une écriture commencée va jusqu'au bout. */
@Composable
fun SavingDialog(message: String = stringResource(R.string.editor_saving)) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        NeoCard(modifier = Modifier.padding(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(message, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
