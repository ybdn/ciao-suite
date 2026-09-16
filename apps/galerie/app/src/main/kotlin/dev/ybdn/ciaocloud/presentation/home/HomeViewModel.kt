package dev.ybdn.ciaocloud.presentation.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ybdn.ciaocloud.di.AppContainer
import dev.ybdn.ciaocloud.domain.model.MediaFile
import dev.ybdn.ciaocloud.domain.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HomeUiState(
    val destinationUri: Uri? = null,
    val isScanning: Boolean = false,
    val scannedFiles: List<MediaFile> = emptyList(),
) {
    val photoCount: Int get() = scannedFiles.count { it.mediaType == MediaType.PHOTO }
    val videoCount: Int get() = scannedFiles.count { it.mediaType == MediaType.VIDEO }
    val totalSizeBytes: Long get() = scannedFiles.sumOf { it.sizeBytes }
    val hasDestination: Boolean get() = destinationUri != null
    val canTransfer: Boolean get() = hasDestination && scannedFiles.isNotEmpty()
}

class HomeViewModel(
    private val appContainer: AppContainer,
    application: Application,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedUri = appContainer.settingsDataStore.destinationRootUri.first()
            _uiState.value = _uiState.value.copy(destinationUri = savedUri)
        }
    }

    fun onDestinationSelected(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        resolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        viewModelScope.launch {
            appContainer.settingsDataStore.setDestinationRootUri(uri)
            _uiState.value = _uiState.value.copy(destinationUri = uri)
        }
    }

    fun scanMedia() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            val files = appContainer.scanLocalMediaUseCase()
            _uiState.value = _uiState.value.copy(isScanning = false, scannedFiles = files)
        }
    }
}
