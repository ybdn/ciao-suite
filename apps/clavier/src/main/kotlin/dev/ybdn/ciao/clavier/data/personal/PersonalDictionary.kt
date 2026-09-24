package dev.ybdn.ciao.clavier.data.personal

import android.content.Context
import androidx.room.withTransaction
import dev.ybdn.ciao.clavier.data.ClavierDatabase
import dev.ybdn.ciao.clavier.domain.suggest.PersonalDictionaryRules
import dev.ybdn.ciao.clavier.domain.suggest.PersonalWord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.util.Locale

/**
 * Dictionnaire personnel (apps/clavier/docs/spec-v1.md §7.3), stocké sur l'appareil seulement :
 * mots appris, candidats et mots souvent choisis dans la barre. Le service n'y écrit jamais en
 * navigation privée ni dans un champ sensible (§3) : c'est à lui de ne pas appeler [keep]/[used].
 */
class PersonalDictionary(context: Context) {

    private val database = ClavierDatabase.get(context)
    private val dao = database.personalWordDao()

    val words: Flow<List<PersonalWord>> = dao.observeAll().map { list -> list.map(PersonalWordEntity::toDomain) }

    /** Mots appris, dans l'ordre alphabétique français (réglages). */
    val learned: Flow<List<PersonalWord>> = words.map { list ->
        val collator = Collator.getInstance(Locale.FRENCH)
        list.filter { it.learned }.sortedWith(compareBy(collator) { it.word })
    }

    /**
     * [word], inconnu du dictionnaire embarqué, a été tapé et conservé (pas corrigé, ou correction
     * annulée) : appris à la deuxième fois.
     */
    suspend fun keep(word: String, now: Long) {
        if (!PersonalDictionaryRules.isLearnable(word)) return
        val key = PersonalDictionaryRules.key(word)
        database.withTransaction {
            val existing = dao.get(key)?.toDomain()
            dao.upsert(PersonalDictionaryRules.kept(existing, word, now).toEntity(key))
            val toForget = PersonalDictionaryRules.candidatesToForget(dao.getAll().map(PersonalWordEntity::toDomain))
            if (toForget.isNotEmpty()) dao.delete(toForget.map { PersonalDictionaryRules.key(it.word) })
        }
    }

    /** [word], un mot connu, a été choisi dans la barre de suggestions : sa fréquence personnelle augmente. */
    suspend fun used(word: String, now: Long) {
        if (!PersonalDictionaryRules.isLearnable(word)) return
        val key = PersonalDictionaryRules.key(word)
        database.withTransaction {
            val existing = dao.get(key)?.toDomain()
            val updated = existing?.copy(uses = existing.uses + 1, lastUsedAt = now)
                ?: PersonalWord(word, uses = 1, learned = false, lastUsedAt = now)
            dao.upsert(updated.toEntity(key))
        }
    }

    /** Ajout manuel depuis les réglages : appris d'emblée, avec la casse saisie. */
    suspend fun add(word: String, now: Long) {
        val key = PersonalDictionaryRules.key(word)
        database.withTransaction {
            val existing = dao.get(key)?.toDomain()
            val updated = existing?.copy(word = word, learned = true, lastUsedAt = now)
                ?: PersonalWord(word, uses = 0, learned = true, lastUsedAt = now)
            dao.upsert(updated.toEntity(key))
        }
    }

    suspend fun delete(word: String) = dao.delete(listOf(PersonalDictionaryRules.key(word)))

    /** Efface tout : mots appris, candidats et fréquences personnelles. */
    suspend fun clear() = dao.deleteAll()
}
