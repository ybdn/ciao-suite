package dev.ybdn.ciao.galerie.data.datastore

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.ybdn.ciao.galerie.domain.model.ThemeMode
import dev.ybdn.ciao.galerie.domain.repository.SharePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "galerie_settings")

/** Persiste l'URI SAF racine du SSD sélectionné et les préférences d'affichage (survivent aux relances de l'app). */
class SettingsDataStore(private val context: Context) : SharePreferences {

    val destinationRootUri: Flow<Uri?> = context.dataStore.data.map { prefs ->
        prefs[DESTINATION_ROOT_URI_KEY]?.let { Uri.parse(it) }
    }

    suspend fun setDestinationRootUri(uri: Uri) {
        context.dataStore.edit { prefs -> prefs[DESTINATION_ROOT_URI_KEY] = uri.toString() }
    }

    suspend fun clearDestinationRootUri() {
        context.dataStore.edit { prefs -> prefs.remove(DESTINATION_ROOT_URI_KEY) }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY]?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[THEME_MODE_KEY] = mode.name }
    }

    /** Taille maximale du cache de vignettes SSD, en octets (500 Mo par défaut). */
    val thumbnailCacheMaxBytes: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[THUMBNAIL_CACHE_MAX_BYTES_KEY] ?: DEFAULT_THUMBNAIL_CACHE_MAX_BYTES
    }

    suspend fun setThumbnailCacheMaxBytes(bytes: Long) {
        context.dataStore.edit { prefs -> prefs[THUMBNAIL_CACHE_MAX_BYTES_KEY] = bytes }
    }

    /** Partage sans métadonnées : désactivé par défaut, le partage garde alors son comportement v2. */
    override fun observeStripMetadata(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SHARE_STRIP_METADATA_KEY] ?: false
    }

    override suspend fun setStripMetadata(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[SHARE_STRIP_METADATA_KEY] = enabled }
    }

    companion object {
        const val DEFAULT_THUMBNAIL_CACHE_MAX_BYTES = 500L * 1024 * 1024

        private val DESTINATION_ROOT_URI_KEY = stringPreferencesKey("destination_root_uri")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val THUMBNAIL_CACHE_MAX_BYTES_KEY = longPreferencesKey("thumbnail_cache_max_bytes")
        private val SHARE_STRIP_METADATA_KEY = booleanPreferencesKey("share_strip_metadata")
    }
}
