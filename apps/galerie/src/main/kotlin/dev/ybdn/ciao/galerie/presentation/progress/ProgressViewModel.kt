package dev.ybdn.ciao.galerie.presentation.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.ybdn.ciao.galerie.di.AppContainer
import dev.ybdn.ciao.galerie.service.TransferForegroundService
import dev.ybdn.ciao.galerie.service.TransferUiState
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
