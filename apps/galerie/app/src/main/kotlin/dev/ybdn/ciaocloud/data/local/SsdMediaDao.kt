package dev.ybdn.ciaocloud.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SsdMediaDao {

    @Query("SELECT * FROM ssd_media")
    fun observeAll(): Flow<List<SsdMediaEntity>>

    @Query("SELECT * FROM ssd_media WHERE relativePath = :relativePath LIMIT 1")
    suspend fun get(relativePath: String): SsdMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SsdMediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SsdMediaEntity>)

    @Query("DELETE FROM ssd_media WHERE relativePath = :relativePath")
    suspend fun delete(relativePath: String)

    @Query("DELETE FROM ssd_media")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(entities: List<SsdMediaEntity>) {
        deleteAll()
        upsertAll(entities)
    }
}
