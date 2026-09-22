package dev.ybdn.ciaocloud.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.model.SsdMedia
import java.time.LocalDate

@Entity(tableName = "ssd_media")
data class SsdMediaEntity(
    /** Insensible à la casse, comme les systèmes de fichiers des SSD (exFAT). */
    @PrimaryKey @ColumnInfo(collate = ColumnInfo.NOCASE) val relativePath: String,
    val displayName: String,
    val mediaType: MediaType,
    val mimeType: String,
    val sizeBytes: Long,
    /** Jour de prise de vue en jours depuis l'epoch (`LocalDate.toEpochDay`). */
    val captureEpochDay: Long,
    val capturedAtEpochMillis: Long?,
    val lastModifiedEpochMillis: Long,
)

fun SsdMediaEntity.toDomain(): SsdMedia = SsdMedia(
    relativePath = relativePath,
    displayName = displayName,
    mediaType = mediaType,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    captureDate = LocalDate.ofEpochDay(captureEpochDay),
    capturedAtEpochMillis = capturedAtEpochMillis,
    lastModifiedEpochMillis = lastModifiedEpochMillis,
)

fun SsdMedia.toEntity(): SsdMediaEntity = SsdMediaEntity(
    relativePath = relativePath,
    displayName = displayName,
    mediaType = mediaType,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    captureEpochDay = captureDate.toEpochDay(),
    capturedAtEpochMillis = capturedAtEpochMillis,
    lastModifiedEpochMillis = lastModifiedEpochMillis,
)
