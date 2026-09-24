package dev.ybdn.ciao.clavier.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dev.ybdn.ciao.clavier.domain.input.TypingSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Réglages de frappe, partagés entre l'app de réglages et le service de clavier. */
class TypingPreferences(context: Context) {

    private val dataStore = context.applicationContext.clavierDataStore

    val settings: Flow<TypingSettings> = dataStore.data.map { prefs ->
        val defaults = TypingSettings()
        TypingSettings(
            autoCapitalize = prefs[AutoCapitalize] ?: defaults.autoCapitalize,
            doubleSpacePeriod = prefs[DoubleSpacePeriod] ?: defaults.doubleSpacePeriod,
            nonBreakingSpace = prefs[NonBreakingSpace] ?: defaults.nonBreakingSpace,
            suggestions = prefs[Suggestions] ?: defaults.suggestions,
            autocorrect = prefs[Autocorrect] ?: defaults.autocorrect,
        )
    }

    suspend fun setAutoCapitalize(enabled: Boolean) = set(AutoCapitalize, enabled)

    suspend fun setDoubleSpacePeriod(enabled: Boolean) = set(DoubleSpacePeriod, enabled)

    suspend fun setNonBreakingSpace(enabled: Boolean) = set(NonBreakingSpace, enabled)

    suspend fun setSuggestions(enabled: Boolean) = set(Suggestions, enabled)

    suspend fun setAutocorrect(enabled: Boolean) = set(Autocorrect, enabled)

    private suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { it[key] = value }
    }

    private companion object {
        val AutoCapitalize = booleanPreferencesKey("auto_capitalize")
        val DoubleSpacePeriod = booleanPreferencesKey("double_space_period")
        val NonBreakingSpace = booleanPreferencesKey("non_breaking_space")
        val Suggestions = booleanPreferencesKey("suggestions")
        val Autocorrect = booleanPreferencesKey("autocorrect")
    }
}
