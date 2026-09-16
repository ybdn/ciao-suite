package dev.ybdn.ciaocloud.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TransferStateEntity::class, SsdMediaEntity::class, FavoriteEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CiaoCloudDatabase : RoomDatabase() {
    abstract fun transferStateDao(): TransferStateDao
    abstract fun ssdMediaDao(): SsdMediaDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        const val DATABASE_NAME = "ciaocloud.db"
    }
}
