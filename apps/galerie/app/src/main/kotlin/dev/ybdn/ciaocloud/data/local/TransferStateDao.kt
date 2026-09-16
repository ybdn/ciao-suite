package dev.ybdn.ciaocloud.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferStateDao {

    // Lecture en transaction : un résultat de plusieurs CursorWindow lu pendant une écriture
    // concurrente (suppression, export) lève « Couldn't read row … from CursorWindow ».
    @Transaction
    @Query("SELECT * FROM transfer_state")
    fun observeAll(): Flow<List<TransferStateEntity>>

    @Query("SELECT * FROM transfer_state WHERE mediaStoreId = :mediaStoreId LIMIT 1")
    suspend fun getByMediaStoreId(mediaStoreId: Long): TransferStateEntity?

    @Query("SELECT * FROM transfer_state WHERE status = :status")
    suspend fun getByStatus(status: TransferStatus): List<TransferStateEntity>

    @Query("SELECT * FROM transfer_state WHERE destinationPath = :destinationPath COLLATE NOCASE")
    suspend fun getByDestinationPath(destinationPath: String): List<TransferStateEntity>

    @Query("SELECT * FROM transfer_state WHERE mediaStoreId IN (:mediaStoreIds)")
    suspend fun getByMediaStoreIds(mediaStoreIds: List<Long>): List<TransferStateEntity>

    @Query("DELETE FROM transfer_state WHERE mediaStoreId = :mediaStoreId")
    suspend fun delete(mediaStoreId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TransferStateEntity)

    @Query(
        "UPDATE transfer_state SET status = :status, errorMessage = :errorMessage " +
            "WHERE mediaStoreId = :mediaStoreId",
    )
    suspend fun updateStatus(mediaStoreId: Long, status: TransferStatus, errorMessage: String?)
}
