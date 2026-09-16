package dev.ybdn.ciaocloud.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.model.TransferRecord
import dev.ybdn.ciaocloud.domain.model.TransferStatus

@Entity(tableName = "transfer_state")
data class TransferStateEntity(
    @PrimaryKey val mediaStoreId: Long,
    val mediaType: MediaType,
    val status: TransferStatus,
    val destinationPath: String?,
    val checksum: String?,
    val errorMessage: String?,
    val sizeBytes: Long = 0,
)

fun TransferStateEntity.toDomain(): TransferRecord = TransferRecord(
    mediaStoreId = mediaStoreId,
    mediaType = mediaType,
    status = status,
    destinationPath = destinationPath,
    checksum = checksum,
    errorMessage = errorMessage,
    sizeBytes = sizeBytes,
)

fun TransferRecord.toEntity(): TransferStateEntity = TransferStateEntity(
    mediaStoreId = mediaStoreId,
    mediaType = mediaType,
    status = status,
    destinationPath = destinationPath,
    checksum = checksum,
    errorMessage = errorMessage,
    sizeBytes = sizeBytes,
)
