package dev.ybdn.ciao.galerie.data.repository

import dev.ybdn.ciao.galerie.data.local.TransferStateDao
import dev.ybdn.ciao.galerie.data.local.toDomain
import dev.ybdn.ciao.galerie.data.local.toEntity
import dev.ybdn.ciao.galerie.domain.model.TransferRecord
import dev.ybdn.ciao.galerie.domain.model.TransferStatus
import dev.ybdn.ciao.galerie.domain.repository.TransferStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransferStateRepositoryImpl(
    private val dao: TransferStateDao,
) : TransferStateRepository {

    override fun observeAll(): Flow<List<TransferRecord>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getByMediaStoreId(mediaStoreId: Long): TransferRecord? =
        dao.getByMediaStoreId(mediaStoreId)?.toDomain()

    override suspend fun getByStatus(status: TransferStatus): List<TransferRecord> =
        dao.getByStatus(status).map { it.toDomain() }

    override suspend fun upsert(record: TransferRecord) {
        dao.upsert(record.toEntity())
    }

    override suspend fun getByDestinationPath(destinationPath: String): List<TransferRecord> =
        dao.getByDestinationPath(destinationPath).map { it.toDomain() }

    override suspend fun getByMediaStoreIds(mediaStoreIds: Collection<Long>): List<TransferRecord> =
        mediaStoreIds.chunked(SQL_IN_CHUNK_SIZE).flatMap { chunk -> dao.getByMediaStoreIds(chunk).map { it.toDomain() } }

    override suspend fun delete(mediaStoreId: Long) = dao.delete(mediaStoreId)

    override suspend fun markStatus(mediaStoreId: Long, status: TransferStatus, errorMessage: String?) {
        dao.updateStatus(mediaStoreId, status, errorMessage)
    }

    private companion object {
        const val SQL_IN_CHUNK_SIZE = 500
    }
}
