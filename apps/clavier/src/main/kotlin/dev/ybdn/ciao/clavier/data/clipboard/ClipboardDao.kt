package dev.ybdn.ciao.clavier.data.clipboard

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {

    @Query("SELECT * FROM clipboard_item")
    fun observeAll(): Flow<List<ClipboardEntity>>

    @Query("SELECT * FROM clipboard_item")
    suspend fun getAll(): List<ClipboardEntity>

    /** Copier de nouveau un texte déjà présent le remonte, en gardant son épinglage. */
    @Transaction
    suspend fun upsertText(text: String, copiedAt: Long) {
        if (touch(text, copiedAt) == 0) insert(ClipboardEntity(text = text, copiedAt = copiedAt))
    }

    @Query("UPDATE clipboard_item SET copiedAt = :copiedAt WHERE text = :text")
    suspend fun touch(text: String, copiedAt: Long): Int

    @Insert
    suspend fun insert(entity: ClipboardEntity)

    @Query("UPDATE clipboard_item SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM clipboard_item WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM clipboard_item")
    suspend fun deleteAll()
}
