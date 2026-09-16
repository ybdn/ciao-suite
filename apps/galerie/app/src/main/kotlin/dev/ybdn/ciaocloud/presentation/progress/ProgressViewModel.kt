package dev.ybdn.ciaocloud.presentation.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.ybdn.ciaocloud.di.AppContainer
import dev.ybdn.ciaocloud.service.TransferForegroundService
import dev.ybdn.ciaocloud.service.TransferUiState
import kotlinx.coroutines.flow.StateFlow

class ProgressViewModel(
    @Suppress("UNUSED_PARAMETER") appContainer: AppContainer,
    application: Application,
) : AndroidViewModel(application) {

    val uiState: StateFlow<TransferUiState> = TransferForegroundService.uiState

    init {
        // Dans init (et non dans un LaunchedEffect) : une rotation ou un retour sur l'écran
        // ne relance pas de transfert.
        TransferForegroundService.start(application)
    }
}
