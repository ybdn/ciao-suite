package dev.ybdn.ciaocloud.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [TransferStateEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class CiaoCloudDatabase : RoomDatabase() {
    abstract fun transferStateDao(): TransferStateDao

    companion object {
        const val DATABASE_NAME = "ciaocloud.db"
    }
}
