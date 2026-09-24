package dev.ybdn.ciao.clavier.data

import android.content.Context
import dev.ybdn.ciao.clavier.domain.suggest.Dictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dictionnaire français embarqué (apps/clavier/docs/spec-v1.md §7.2) : `assets/dictionary/fr.dict`,
 * compilé au build depuis `apps/clavier/dictionary/` (Leipzig Corpora Collection, CC BY 4.0). Lu une
 * fois par processus, en arrière-plan.
 */
object FrenchDictionary {

    @Volatile
    private var cached: Dictionary? = null

    suspend fun load(context: Context): Dictionary = cached ?: withContext(Dispatchers.IO) {
        cached ?: context.assets.open("dictionary/fr.dict").use(Dictionary::read).also { cached = it }
    }
}
