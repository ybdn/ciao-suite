package dev.ybdn.ciao.clavier.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * Unique DataStore de l'app (réglages, emojis récents…), partagé par l'app de réglages et le
 * service de clavier, qui tournent dans le même processus. Jamais sauvegardé hors de l'appareil :
 * `allowBackup="false"`.
 */
internal val Context.clavierDataStore by preferencesDataStore(name = "clavier_settings")
