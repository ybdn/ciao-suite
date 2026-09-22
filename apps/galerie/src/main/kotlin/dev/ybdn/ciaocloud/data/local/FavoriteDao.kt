package dev.ybdn.ciaocloud.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    // Lecture en transaction : un résultat de plusieurs CursorWindow lu pendant une écriture
    // concurrente (suppression, export) lève « Couldn't read row … from CursorWindow ».
    @Transaction
    @Query("SELECT `key` FROM favorites")
    fun observeKeys(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE `key` IN (:keys)")
    suspend fun deleteAll(keys: List<String>)

    /** Déplace un favori vers une nouvelle clé ; sans effet si l'ancienne clé n'est pas favorite. */
    @Query("UPDATE OR REPLACE favorites SET `key` = :newKey WHERE `key` = :oldKey")
    suspend fun renameKey(oldKey: String, newKey: String)
}
