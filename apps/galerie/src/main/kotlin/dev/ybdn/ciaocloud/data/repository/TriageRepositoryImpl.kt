package dev.ybdn.ciaocloud.data.repository

import dev.ybdn.ciaocloud.data.local.TriageStateDao
import dev.ybdn.ciaocloud.data.local.toDomain
import dev.ybdn.ciaocloud.data.local.toEntity
import dev.ybdn.ciaocloud.domain.model.TriageDecision
import dev.ybdn.ciaocloud.domain.model.TriageState
import dev.ybdn.ciaocloud.domain.repository.TriageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class TriageRepositoryImpl(private val dao: TriageStateDao) : TriageRepository {

    override fun observeAll(): Flow<List<TriageState>> =
        dao.observeAll().distinctUntilChanged().map { entities -> entities.map { it.toDomain() } }

    override suspend fun upsert(state: TriageState) = dao.upsert(state.toEntity())

    override suspend fun delete(keys: Collection<String>) {
        keys.distinct().chunked(SQL_IN_CHUNK_SIZE).forEach { dao.deleteAll(it) }
    }

    override suspend fun deleteByDecisions(decisions: Set<TriageDecision>) {
        if (decisions.isNotEmpty()) dao.deleteByDecisions(decisions.toList())
    }

    override suspend fun renameKey(oldKey: String, newKey: String) = dao.renameKey(oldKey, newKey)

    private companion object {
        const val SQL_IN_CHUNK_SIZE = 500
    }
}
