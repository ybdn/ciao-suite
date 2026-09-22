package dev.ybdn.ciaocloud.presentation.deleteconfirm

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.di.AppContainer
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeleteConfirmationUiState(
    val isLoading: Boolean = true,
    val verifiedCount: Int = 0,
    val freeableBytes: Long = 0,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null,
) {
    val canDelete: Boolean get() = !isLoading && !isDeleting && verifiedCount > 0
}

class DeleteConfirmationViewModel(
    private val appContainer: AppContainer,
    application: Application,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DeleteConfirmationUiState())
    val uiState: StateFlow<DeleteConfirmationUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val verifiedRecords = appContainer.transferStateRepository.getByStatus(TransferStatus.VERIFIED)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    verifiedCount = verifiedRecords.size,
                    freeableBytes = verifiedRecords.sumOf { record -> record.sizeBytes },
                )
            }
        }
    }

    fun confirmDeletion() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
            val app = getApplication<Application>()
            val errorMessage = try {
                val outcome = appContainer.deleteVerifiedMediaUseCase()
                when {
                    outcome.isComplete -> null
                    outcome.deleted > 0 -> app.getString(R.string.delete_partial, outcome.deleted, outcome.requested)
                    else -> app.getString(R.string.delete_refused)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Échec de la demande de suppression", e)
                app.getString(R.string.delete_error, e.message ?: e::class.simpleName)
            }
            _uiState.update { it.copy(isDeleting = false, isDeleted = errorMessage == null, errorMessage = errorMessage) }
            if (errorMessage != null) refresh()
        }
    }

    private companion object {
        const val TAG = "DeleteConfirmation"
    }
}
