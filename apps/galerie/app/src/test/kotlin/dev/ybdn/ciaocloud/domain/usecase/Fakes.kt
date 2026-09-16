package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.SsdMedia
import dev.ybdn.ciaocloud.domain.model.TransferRecord
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import dev.ybdn.ciaocloud.domain.repository.DestinationEntry
import dev.ybdn.ciaocloud.domain.repository.DestinationWriteResult
import dev.ybdn.ciaocloud.domain.repository.DestinationWriter
import dev.ybdn.ciaocloud.domain.repository.FavoritesRepository
import dev.ybdn.ciaocloud.domain.repository.MediaTrash
import dev.ybdn.ciaocloud.domain.repository.SsdFileEntry
import dev.ybdn.ciaocloud.domain.repository.SsdMediaBrowser
import dev.ybdn.ciaocloud.domain.repository.SsdMediaIndex
import dev.ybdn.ciaocloud.domain.repository.SsdThumbnailCache
import dev.ybdn.ciaocloud.domain.repository.TransactionRunner
import dev.ybdn.ciaocloud.domain.repository.TransferStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class FakeTransferStateRepository : TransferStateRepository {
    val records = MutableStateFlow<Map<Long, TransferRecord>>(emptyMap())

    override fun observeAll(): Flow<List<TransferRecord>> = records.map { it.values.toList() }
    override suspend fun getByMediaStoreId(mediaStoreId: Long) = records.value[mediaStoreId]
    override suspend fun getByStatus(status: TransferStatus) = records.value.values.filter { it.status == status }
    override suspend fun upsert(record: TransferRecord) {
        records.value = records.value + (record.mediaStoreId to record)
    }
    override suspend fun getByDestinationPath(destinationPath: String) =
        records.value.values.filter { it.destinationPath.equals(destinationPath, ignoreCase = true) }
    override suspend fun getByMediaStoreIds(mediaStoreIds: Collection<Long>) =
        records.value.values.filter { it.mediaStoreId in mediaStoreIds }
    override suspend fun delete(mediaStoreId: Long) {
        records.value = records.value - mediaStoreId
    }
    override suspend fun markStatus(mediaStoreId: Long, status: TransferStatus, errorMessage: String?) {
        records.value[mediaStoreId]?.let { upsert(it.copy(status = status, errorMessage = errorMessage)) }
    }
}

class FakeSsdMediaIndex : SsdMediaIndex {
    val media = MutableStateFlow<Map<String, SsdMedia>>(emptyMap())

    override fun observeAll(): Flow<List<SsdMedia>> = media.map { it.values.toList() }
    override suspend fun get(relativePath: String) = media.value[relativePath.lowercase()]
    override suspend fun upsert(media: SsdMedia) {
        this.media.value = this.media.value + (media.relativePath.lowercase() to media)
    }
    override suspend fun remove(relativePath: String) {
        media.value = media.value - relativePath.lowercase()
    }
    override suspend fun replaceAll(media: List<SsdMedia>) {
        this.media.value = media.associateBy { it.relativePath.lowercase() }
    }
}

class FakeFavoritesRepository(initial: Set<String> = emptySet()) : FavoritesRepository {
    val keys = MutableStateFlow(initial)

    override fun observeKeys(): Flow<Set<String>> = keys
    override suspend fun setFavorite(keys: Collection<String>, favorite: Boolean) {
        this.keys.value = if (favorite) this.keys.value + keys else this.keys.value - keys.toSet()
    }
    override suspend fun renameKey(oldKey: String, newKey: String) {
        if (oldKey in keys.value) keys.value = keys.value - oldKey + newKey
    }
}

class FakeThumbnailCache : SsdThumbnailCache {
    val removed = mutableListOf<String>()
    override suspend fun seedFromPhone(phoneUri: String, relativePath: String) = Unit
    override suspend fun remove(relativePath: String) {
        removed += relativePath
    }
    override suspend fun sizeBytes() = 0L
    override suspend fun clear() = Unit
}

object DirectTransactionRunner : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
}

class FakeMediaTrash(var accept: Boolean = true) : MediaTrash {
    val trashed = mutableListOf<String>()
    override suspend fun moveToTrash(uris: List<String>) = accept.also { if (it) trashed += uris }
    override suspend fun restore(uris: List<String>) = accept
    override suspend fun deletePermanently(uris: List<String>) = accept
}

class FakeSsdMediaBrowser(available: Boolean = true) : SsdMediaBrowser {
    val deleted = mutableListOf<String>()
    override val isAvailable: StateFlow<Boolean> = MutableStateFlow(available)
    override suspend fun refreshAvailability() = isAvailable.value
    override suspend fun listDayFolderMedia(onProgress: (Int, Int) -> Unit): List<SsdFileEntry>? = emptyList()
    override suspend fun documentUri(relativePath: String) = "content://ssd/$relativePath"
    override suspend fun delete(relativePath: String) = true.also { deleted += relativePath }
}

/** Destination en mémoire : dossiers → (nom → contenu). */
class FakeDestinationWriter : DestinationWriter {
    val files = HashMap<String, MutableMap<String, ByteArray>>()
    val sources = HashMap<String, ByteArray>()

    override suspend fun isDestinationAvailable() = true
    override suspend fun destinationName() = "SSD"
    override suspend fun phoneStorageRelativePath(): String? = null
    override suspend fun availableBytes(): Long? = null
    override suspend fun listExistingEntries(relativeDirPath: String) =
        files[relativeDirPath].orEmpty().map { (name, bytes) -> DestinationEntry(name, bytes.size.toLong(), false) }
    override suspend fun identicalContentChecksum(
        sourceUri: String,
        relativeDirPath: String,
        fileName: String,
        onProgress: (bytesRead: Long) -> Unit,
    ): String? = if (files[relativeDirPath]!![fileName]!!.contentEquals(sources[sourceUri]!!)) "crc" else null
    override suspend fun writeFile(
        sourceUri: String,
        relativeDirPath: String,
        fileName: String,
        onProgress: (bytesCopied: Long) -> Unit,
    ): DestinationWriteResult {
        val bytes = sources[sourceUri]!!
        files.getOrPut(relativeDirPath) { HashMap() }[fileName] = bytes
        return DestinationWriteResult("$relativeDirPath/$fileName", fileName, bytes.size.toLong(), "crc")
    }
    override suspend fun readBackForVerification(relativeDirPath: String, fileName: String): DestinationWriteResult? {
        val bytes = files[relativeDirPath]?.get(fileName) ?: return null
        return DestinationWriteResult("$relativeDirPath/$fileName", fileName, bytes.size.toLong(), "crc")
    }
}
