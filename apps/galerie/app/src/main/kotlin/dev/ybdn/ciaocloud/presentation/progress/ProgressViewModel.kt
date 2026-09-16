package dev.ybdn.ciaocloud.presentation.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ybdn.ciaocloud.di.AppContainer
import dev.ybdn.ciaocloud.service.TransferForegroundService
import dev.ybdn.ciaocloud.service.TransferUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ProgressViewModel(
    @Suppress("UNUSED_PARAMETER") appContainer: AppContainer,
    application: Application,
) : AndroidViewModel(application) {

    val uiState: StateFlow<TransferUiState> = TransferForegroundService.uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransferUiState(),
    )

    fun startTransfer() {
        TransferForegroundService.start(getApplication())
    }
}
