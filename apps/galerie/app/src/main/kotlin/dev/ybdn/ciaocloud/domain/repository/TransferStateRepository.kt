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

    /** Enregistrements dont la copie est [destinationPath] (casse ignorée, comme sur le SSD). */
    suspend fun getByDestinationPath(destinationPath: String): List<TransferRecord>

    suspend fun getByMediaStoreIds(mediaStoreIds: Collection<Long>): List<TransferRecord>

    /** Oublie l'état de transfert : le média redevient « jamais transféré ». */
    suspend fun delete(mediaStoreId: Long)

    suspend fun markStatus(mediaStoreId: Long, status: TransferStatus, errorMessage: String? = null)
}
