package dev.ybdn.ciao.galerie.presentation.home

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ybdn.ciao.galerie.di.AppContainer
import dev.ybdn.ciao.galerie.domain.model.MediaFile
import dev.ybdn.ciao.galerie.domain.model.MediaType
import dev.ybdn.ciao.galerie.domain.usecase.DestinationStatus
import dev.ybdn.ciao.galerie.service.TransferForegroundService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val destinationUri: Uri? = null,
    val destinationStatus: DestinationStatus? = null,
    val isScanning: Boolean = false,
    val scanError: String? = null,
    /** `null` tant qu'aucun scan n'a été fait. */
    val scannedFiles: List<MediaFile>? = null,
    val isTransferRunning: Boolean = false,
) {
    val photoCount: Int get() = scannedFiles.orEmpty().count { it.mediaType == MediaType.PHOTO }
    val videoCount: Int get() = scannedFiles.orEmpty().count { it.mediaType == MediaType.VIDEO }
    val totalSizeBytes: Long get() = scannedFiles.orEmpty().sumOf { it.sizeBytes }
    val isDestinationAvailable: Boolean get() = destinationStatus?.isAvailable == true
    val hasEnoughSpace: Boolean
        get() = destinationStatus?.availableBytes?.let { it >= totalSizeBytes } ?: true
    val canTransfer: Boolean
        get() = isTransferRunning ||
            (isDestinationAvailable && !scannedFiles.isNullOrEmpty() && hasEnoughSpace && !isScanning)
}

private data class LocalState(
    val destinationUri: Uri? = null,
    val destinationStatus: DestinationStatus? = null,
    val isScanning: Boolean = false,
    val scanError: String? = null,
)

class HomeViewModel(
    private val appContainer: AppContainer,
    application: Application,
) : AndroidViewModel(application) {

    private val localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<HomeUiState> = combine(
        localState,
        appContainer.scanSession.files,
        TransferForegroundService.uiState,
    ) { local, files, transfer ->
        HomeUiState(
            destinationUri = local.destinationUri,
            destinationStatus = local.destinationStatus,
            isScanning = local.isScanning,
            scanError = local.scanError,
            scannedFiles = files,
            isTransferRunning = transfer.isRunning,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    /** Appelé à chaque retour au premier plan : le SSD a pu être branché/débranché entre-temps. */
    fun refreshDestination() {
        viewModelScope.launch {
            val uri = appContainer.settingsDataStore.destinationRootUri.first()
            val status = if (uri != null) appContainer.getDestinationStatusUseCase() else null
            localState.update { it.copy(destinationUri = uri, destinationStatus = status) }
        }
    }

    fun onDestinationSelected(uri: Uri) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            // Libère l'ancienne permission : le nombre de permissions persistées par app est limité.
            localState.value.destinationUri?.takeIf { it != uri }?.let { old ->
                runCatching { resolver.releasePersistableUriPermission(old, PERSISTED_FLAGS) }
            }
            resolver.takePersistableUriPermission(uri, PERSISTED_FLAGS)
            appContainer.settingsDataStore.setDestinationRootUri(uri)
            refreshDestination()
        }
    }

    fun scanMedia() {
        if (localState.value.isScanning) return
        viewModelScope.launch {
            localState.update { it.copy(isScanning = true, scanError = null) }
            try {
                appContainer.scanLocalMediaUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Échec du scan", e)
                localState.update { it.copy(scanError = e.message ?: e::class.simpleName) }
            } finally {
                localState.update { it.copy(isScanning = false) }
            }
            refreshDestination()
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
        const val PERSISTED_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
