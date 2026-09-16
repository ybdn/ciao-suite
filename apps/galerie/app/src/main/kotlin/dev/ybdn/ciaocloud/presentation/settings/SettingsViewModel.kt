package dev.ybdn.ciaocloud.presentation.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ybdn.ciaocloud.di.AppContainer
import dev.ybdn.ciaocloud.domain.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
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
