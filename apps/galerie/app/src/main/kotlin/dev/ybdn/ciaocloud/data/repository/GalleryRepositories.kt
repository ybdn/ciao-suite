package dev.ybdn.ciaocloud.data.repository

import androidx.room.withTransaction
import dev.ybdn.ciaocloud.data.local.CiaoCloudDatabase
import dev.ybdn.ciaocloud.data.local.FavoriteDao
import dev.ybdn.ciaocloud.data.local.FavoriteEntity
import dev.ybdn.ciaocloud.data.local.SsdMediaDao
import dev.ybdn.ciaocloud.data.local.toDomain
import dev.ybdn.ciaocloud.data.local.toEntity
import dev.ybdn.ciaocloud.domain.model.SsdMedia
import dev.ybdn.ciaocloud.domain.repository.FavoritesRepository
import dev.ybdn.ciaocloud.domain.repository.SsdMediaIndex
import dev.ybdn.ciaocloud.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomSsdMediaIndex(private val dao: SsdMediaDao) : SsdMediaIndex {

    override fun observeAll(): Flow<List<SsdMedia>> =
        dao.observeAll().distinctUntilChanged().map { entities -> entities.map { it.toDomain() } }

    override suspend fun get(relativePath: String): SsdMedia? = dao.get(relativePath)?.toDomain()

    override suspend fun upsert(media: SsdMedia) = dao.upsert(media.toEntity())

    override suspend fun remove(relativePath: String) = dao.delete(relativePath)

    override suspend fun replaceAll(media: List<SsdMedia>) = dao.replaceAll(media.map { it.toEntity() })
}

class RoomFavoritesRepository(private val dao: FavoriteDao) : FavoritesRepository {

    override fun observeKeys(): Flow<Set<String>> = dao.observeKeys().map { it.toSet() }.distinctUntilChanged()

    override suspend fun setFavorite(keys: Collection<String>, favorite: Boolean) {
        if (keys.isEmpty()) return
        if (favorite) {
            val now = System.currentTimeMillis()
            dao.insertAll(keys.map { FavoriteEntity(it, now) })
        } else {
            keys.chunked(SQL_IN_CHUNK_SIZE).forEach { dao.deleteAll(it) }
        }
    }

    override suspend fun renameKey(oldKey: String, newKey: String) = dao.renameKey(oldKey, newKey)

    private companion object {
        const val SQL_IN_CHUNK_SIZE = 500
    }
}

class RoomTransactionRunner(private val database: CiaoCloudDatabase) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = database.withTransaction { block() }
}
