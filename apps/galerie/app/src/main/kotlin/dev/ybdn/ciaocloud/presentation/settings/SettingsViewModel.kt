package dev.ybdn.ciaocloud.presentation.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ybdn.ciaocloud.di.AppContainer
import dev.ybdn.ciaocloud.domain.model.ThemeMode
import dev.ybdn.ciaocloud.data.datastore.SettingsDataStore
import dev.ybdn.ciaocloud.domain.usecase.SsdIndexState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appContainer: AppContainer,
    application: Application,
) : AndroidViewModel(application) {

    val destinationUri: StateFlow<Uri?> = appContainer.settingsDataStore.destinationRootUri.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val themeMode: StateFlow<ThemeMode> = appContainer.settingsDataStore.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeMode.SYSTEM,
    )

    val thumbnailCacheMaxBytes: StateFlow<Long> = appContainer.settingsDataStore.thumbnailCacheMaxBytes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsDataStore.DEFAULT_THUMBNAIL_CACHE_MAX_BYTES,
    )

    private val _thumbnailCacheUsedBytes = MutableStateFlow<Long?>(null)
    val thumbnailCacheUsedBytes: StateFlow<Long?> = _thumbnailCacheUsedBytes.asStateFlow()

    val shareStripMetadata: StateFlow<Boolean> = appContainer.shareMetadataSettingUseCase.observe().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun onShareStripMetadataChanged(enabled: Boolean) {
        viewModelScope.launch { appContainer.shareMetadataSettingUseCase.set(enabled) }
    }

    val ssdIndexState: StateFlow<SsdIndexState> = appContainer.refreshSsdIndexUseCase.state

    init {
        refreshThumbnailCacheUsage()
    }

    fun refreshSsdIndex() = appContainer.refreshSsdIndexUseCase.start()

    fun onThumbnailCacheMaxBytesSelected(bytes: Long) {
        viewModelScope.launch { appContainer.settingsDataStore.setThumbnailCacheMaxBytes(bytes) }
    }

    fun clearThumbnailCache() {
        viewModelScope.launch {
            appContainer.manageThumbnailCacheUseCase.clear()
            refreshThumbnailCacheUsage()
        }
    }

    private fun refreshThumbnailCacheUsage() {
        viewModelScope.launch { _thumbnailCacheUsedBytes.value = appContainer.manageThumbnailCacheUseCase.sizeBytes() }
    }

    /** Remet dans la pile de tri les médias gardés ou mis de côté (la file de suppression est conservée). */
    fun resetTriage(onDone: () -> Unit) {
        viewModelScope.launch {
            appContainer.resetTriageUseCase()
            onDone()
        }
    }

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { appContainer.settingsDataStore.setThemeMode(mode) }
    }

    fun onDestinationSelected(uri: Uri) {
        getApplication<Application>().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        viewModelScope.launch { appContainer.settingsDataStore.setDestinationRootUri(uri) }
    }
}
