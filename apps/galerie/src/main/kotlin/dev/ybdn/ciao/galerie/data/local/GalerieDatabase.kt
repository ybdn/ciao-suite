package dev.ybdn.ciao.galerie.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TransferStateEntity::class, SsdMediaEntity::class, FavoriteEntity::class, TriageStateEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class GalerieDatabase : RoomDatabase() {
    abstract fun transferStateDao(): TransferStateDao
    abstract fun ssdMediaDao(): SsdMediaDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun triageStateDao(): TriageStateDao

    companion object {
        const val DATABASE_NAME = "galerie.db"
    }
}
