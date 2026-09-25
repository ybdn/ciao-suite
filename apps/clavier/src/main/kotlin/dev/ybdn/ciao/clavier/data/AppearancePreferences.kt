package dev.ybdn.ciao.clavier.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.ybdn.ciao.clavier.domain.appearance.AppearanceSettings
import dev.ybdn.ciao.clavier.domain.appearance.KeyboardHeight
import dev.ybdn.ciao.clavier.domain.appearance.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Réglages d'apparence (apps/clavier/docs/spec-v1.md §5.3, §10.2), partagés avec le service de clavier. */
class AppearancePreferences(context: Context) {

    private val dataStore = context.applicationContext.clavierDataStore

    val settings: Flow<AppearanceSettings> = dataStore.data.map { prefs ->
        val defaults = AppearanceSettings()
        AppearanceSettings(
            themeMode = prefs[ThemeModeKey]?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                ?: defaults.themeMode,
            keyboardHeight = prefs[KeyboardHeightKey]?.let { stored -> KeyboardHeight.entries.firstOrNull { it.name == stored } }
                ?: defaults.keyboardHeight,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[ThemeModeKey] = mode.name }
    }

    suspend fun setKeyboardHeight(height: KeyboardHeight) {
        dataStore.edit { it[KeyboardHeightKey] = height.name }
    }

    private companion object {
        val ThemeModeKey = stringPreferencesKey("theme_mode")
        val KeyboardHeightKey = stringPreferencesKey("keyboard_height")
    }
}
