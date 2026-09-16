package dev.ybdn.ciaocloud.data.datastore

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ciaocloud_settings")

/** Persiste l'URI SAF racine du SSD sélectionné par l'utilisateur (survit aux relances de l'app). */
class SettingsDataStore(private val context: Context) {

    val destinationRootUri: Flow<Uri?> = context.dataStore.data.map { prefs ->
        prefs[DESTINATION_ROOT_URI_KEY]?.let { Uri.parse(it) }
    }

    suspend fun setDestinationRootUri(uri: Uri) {
        context.dataStore.edit { prefs -> prefs[DESTINATION_ROOT_URI_KEY] = uri.toString() }
    }

    suspend fun clearDestinationRootUri() {
        context.dataStore.edit { prefs -> prefs.remove(DESTINATION_ROOT_URI_KEY) }
    }

    private companion object {
        val DESTINATION_ROOT_URI_KEY = stringPreferencesKey("destination_root_uri")
    }
}
