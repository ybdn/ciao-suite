package dev.ybdn.ciaocloud.presentation.deleteconfirm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ybdn.ciaocloud.di.AppContainer
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeleteConfirmationUiState(
    val verifiedCount: Int = 0,
    val freeableBytes: Long = 0,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null,
)

class DeleteConfirmationViewModel(
    private val appContainer: AppContainer,
    application: Application,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DeleteConfirmationUiState())
    val uiState: StateFlow<DeleteConfirmationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val verifiedRecords = appContainer.transferStateRepository.getByStatus(TransferStatus.VERIFIED)
            _uiState.value = _uiState.value.copy(
                verifiedCount = verifiedRecords.size,
                freeableBytes = verifiedRecords.sumOf { it.sizeBytes },
            )
        }
    }

    fun confirmDeletion() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)
            val outcome = appContainer.deleteVerifiedMediaUseCase()
            _uiState.value = _uiState.value.copy(
                isDeleting = false,
                isDeleted = outcome.granted,
                errorMessage = if (!outcome.granted) "Suppression refusée ou annulée" else null,
            )
        }
    }
}
