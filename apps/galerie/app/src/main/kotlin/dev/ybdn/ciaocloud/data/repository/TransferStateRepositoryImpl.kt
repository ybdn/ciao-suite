package dev.ybdn.ciaocloud.data.repository

import dev.ybdn.ciaocloud.data.local.TransferStateDao
import dev.ybdn.ciaocloud.data.local.toDomain
import dev.ybdn.ciaocloud.data.local.toEntity
import dev.ybdn.ciaocloud.domain.model.TransferRecord
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import dev.ybdn.ciaocloud.domain.repository.TransferStateRepository
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

    override suspend fun markStatus(mediaStoreId: Long, status: TransferStatus, errorMessage: String?) {
        dao.updateStatus(mediaStoreId, status, errorMessage)
    }
}
