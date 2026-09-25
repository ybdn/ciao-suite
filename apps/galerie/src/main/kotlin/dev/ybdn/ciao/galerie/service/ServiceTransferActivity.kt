package dev.ybdn.ciao.galerie.service

import dev.ybdn.ciao.galerie.domain.repository.TransferActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Transfert en cours d'après l'état publié par [TransferForegroundService]. */
class ServiceTransferActivity(scope: CoroutineScope) : TransferActivity {
    override val isRunning: StateFlow<Boolean> = TransferForegroundService.uiState
        .map { it.isRunning }
        .stateIn(scope, SharingStarted.Eagerly, TransferForegroundService.uiState.value.isRunning)
}
