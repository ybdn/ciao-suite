package dev.ybdn.ciao.clavier.data.clipboard

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.ybdn.ciao.clavier.data.clavierDataStore
import dev.ybdn.ciao.clavier.domain.clipboard.ClipboardRetention
import dev.ybdn.ciao.clavier.domain.clipboard.ClipboardSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Réglages de l'historique du presse-papiers (apps/clavier/docs/spec-v1.md §9). */
class ClipboardPreferences(context: Context) {

    private val dataStore = context.applicationContext.clavierDataStore

    val settings: Flow<ClipboardSettings> = dataStore.data.map { prefs ->
        val defaults = ClipboardSettings()
        ClipboardSettings(
            historyEnabled = prefs[HistoryEnabled] ?: defaults.historyEnabled,
            retention = prefs[Retention]?.let { stored -> ClipboardRetention.entries.firstOrNull { it.name == stored } }
                ?: defaults.retention,
        )
    }

    suspend fun setHistoryEnabled(enabled: Boolean) {
        dataStore.edit { it[HistoryEnabled] = enabled }
    }

    suspend fun setRetention(retention: ClipboardRetention) {
        dataStore.edit { it[Retention] = retention.name }
    }

    private companion object {
        val HistoryEnabled = booleanPreferencesKey("clipboard_history_enabled")
        val Retention = stringPreferencesKey("clipboard_retention")
    }
}
