package dev.ybdn.ciao.clavier.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.ybdn.ciao.clavier.data.clipboard.ClipboardDao
import dev.ybdn.ciao.clavier.data.clipboard.ClipboardEntity
import dev.ybdn.ciao.clavier.data.personal.PersonalWordDao
import dev.ybdn.ciao.clavier.data.personal.PersonalWordEntity

/**
 * Base locale du clavier (apps/clavier/docs/spec-v1.md §4) : historique du presse-papiers et
 * dictionnaire personnel. Exclue des sauvegardes (`data_extraction_rules.xml`).
 *
 * Schémas versionnés dans `apps/clavier/schemas` : jamais de migration destructive.
 */
@Database(
    entities = [ClipboardEntity::class, PersonalWordEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class ClavierDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao

    abstract fun personalWordDao(): PersonalWordDao

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
