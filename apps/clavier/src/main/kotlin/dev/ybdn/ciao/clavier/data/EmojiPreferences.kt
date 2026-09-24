package dev.ybdn.ciao.clavier.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.ybdn.ciao.clavier.domain.emoji.RecentEmojis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Emojis récents et couleur de peau choisie pour chaque emoji (apps/clavier/docs/spec-v1.md §8),
 * stockés sur l'appareil seulement.
 */
class EmojiPreferences(context: Context) {

    private val dataStore = context.applicationContext.clavierDataStore

    val recents: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[Recents]?.split(ItemSeparator)?.filter { it.isNotEmpty() }.orEmpty()
    }

    /** Emoji de base → variante de couleur de peau choisie en dernier. */
    val skinTones: Flow<Map<String, String>> = dataStore.data.map { prefs ->
        prefs[SkinTones]?.split(ItemSeparator)
            ?.mapNotNull { entry -> entry.split(PairSeparator).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            ?.toMap()
            .orEmpty()
    }

    suspend fun addRecent(emoji: String) {
        dataStore.edit { prefs ->
            val current = prefs[Recents]?.split(ItemSeparator)?.filter { it.isNotEmpty() }.orEmpty()
            prefs[Recents] = RecentEmojis.add(current, emoji).joinToString(ItemSeparator)
        }
    }

    suspend fun setSkinTone(base: String, variant: String) {
        dataStore.edit { prefs ->
            val current = prefs[SkinTones]?.split(ItemSeparator)?.filter { it.isNotEmpty() }.orEmpty()
                .filterNot { it.startsWith(base + PairSeparator) }
            prefs[SkinTones] = (current + (base + PairSeparator + variant)).joinToString(ItemSeparator)
        }
    }

    suspend fun clearRecents() {
        dataStore.edit { it.remove(Recents) }
    }

    private companion object {
        val Recents = stringPreferencesKey("emoji_recents")
        val SkinTones = stringPreferencesKey("emoji_skin_tones")

        /** Séparateurs ASCII de contrôle : jamais présents dans un emoji. */
        const val ItemSeparator = "\u001E"
        const val PairSeparator = "\u001F"
    }
}
