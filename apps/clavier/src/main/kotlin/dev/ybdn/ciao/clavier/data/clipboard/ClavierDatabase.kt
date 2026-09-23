package dev.ybdn.ciao.clavier.data.clipboard

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base locale du clavier (apps/clavier/docs/spec-v1.md §4) : historique du presse-papiers, puis
 * dictionnaire personnel (lot 5). Exclue des sauvegardes (`data_extraction_rules.xml`).
 */
@Database(entities = [ClipboardEntity::class], version = 1, exportSchema = true)
abstract class ClavierDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao

    companion object {
        @Volatile
        private var instance: ClavierDatabase? = null

        /** Une seule instance par processus, partagée par le service et l'app de réglages. */
        fun get(context: Context): ClavierDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ClavierDatabase::class.java, "clavier.db")
                .build()
                .also { instance = it }
        }
    }
}
