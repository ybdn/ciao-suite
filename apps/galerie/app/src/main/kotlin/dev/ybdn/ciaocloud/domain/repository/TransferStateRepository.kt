package dev.ybdn.ciaocloud.domain.repository

import dev.ybdn.ciaocloud.domain.model.TransferRecord
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import kotlinx.coroutines.flow.Flow

/** Persistance locale (Room) de l'état de transfert, pour ne jamais re-proposer un fichier déjà vérifié. */
interface TransferStateRepository {

    fun observeAll(): Flow<List<TransferRecord>>

    suspend fun getByMediaStoreId(mediaStoreId: Long): TransferRecord?

    suspend fun getByStatus(status: TransferStatus): List<TransferRecord>

    suspend fun upsert(record: TransferRecord)

    suspend fun markStatus(mediaStoreId: Long, status: TransferStatus, errorMessage: String? = null)
}
