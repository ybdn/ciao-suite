package dev.ybdn.ciao.galerie.presentation.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ybdn.ciao.galerie.R
import dev.ybdn.ciao.galerie.domain.usecase.SsdIndexState
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoNotice
import dev.ybdn.ciao.designsystem.components.NeoTone

/** Bouton « Actualiser le SSD » et état de la réindexation (Réglages et galerie vide). */
@Composable
fun SsdIndexControls(
    state: SsdIndexState,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NeoButton(
            text = stringResource(R.string.ssd_index_refresh),
            onClick = onRefresh,
            tone = NeoTone.Primary,
            enabled = state !is SsdIndexState.Running,
        )
        when (state) {
            SsdIndexState.Idle -> Unit
            is SsdIndexState.Running -> NeoNotice(
                stringResource(R.string.ssd_index_running, state.dayFoldersScanned, state.filesFound),
                tone = NeoTone.Surface,
            )
            is SsdIndexState.Done -> NeoNotice(stringResource(R.string.ssd_index_done, state.indexedCount), tone = NeoTone.Success)
            SsdIndexState.DestinationUnavailable -> NeoNotice(stringResource(R.string.ssd_index_unavailable), tone = NeoTone.Danger)
            is SsdIndexState.Failed -> NeoNotice(stringResource(R.string.ssd_index_failed, state.reason), tone = NeoTone.Danger)
        }
    }
}
