package dev.ybdn.ciao.clavier.data.personal

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.ybdn.ciao.clavier.domain.suggest.PersonalWord

/** Un mot du dictionnaire personnel, rangé sous sa clé sans casse ([dev.ybdn.ciao.clavier.domain.suggest.PersonalDictionaryRules.key]). */
@Entity(tableName = "personal_word")
data class PersonalWordEntity(
    @PrimaryKey val key: String,
    val word: String,
    val uses: Int,
    val learned: Boolean,
    val lastUsedAt: Long,
) {
    fun toDomain() = PersonalWord(word = word, uses = uses, learned = learned, lastUsedAt = lastUsedAt)
}

fun PersonalWord.toEntity(key: String) =
    PersonalWordEntity(key = key, word = word, uses = uses, learned = learned, lastUsedAt = lastUsedAt)
