package dev.ybdn.ciaocloud.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    /** `phone:<mediaStoreId>` ou `ssd:<chemin relatif>` (voir `FavoriteKeys`). */
    @PrimaryKey val key: String,
    val addedAtEpochMillis: Long,
)
