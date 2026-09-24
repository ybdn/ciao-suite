package dev.ybdn.ciao.clavier.data.personal

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalWordDao {

    @Query("SELECT * FROM personal_word")
    fun observeAll(): Flow<List<PersonalWordEntity>>

    @Query("SELECT * FROM personal_word")
    suspend fun getAll(): List<PersonalWordEntity>

    @Query("SELECT * FROM personal_word WHERE `key` = :key")
    suspend fun get(key: String): PersonalWordEntity?

    @Upsert
    suspend fun upsert(entity: PersonalWordEntity)

    @Query("DELETE FROM personal_word WHERE `key` IN (:keys)")
    suspend fun delete(keys: List<String>)

    @Query("DELETE FROM personal_word")
    suspend fun deleteAll()
}
