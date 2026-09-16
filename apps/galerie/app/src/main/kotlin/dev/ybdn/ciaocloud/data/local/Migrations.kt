package dev.ybdn.ciaocloud.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.ybdn.ciaocloud.domain.util.MediaFileTypes
import dev.ybdn.ciaocloud.domain.util.TimelineBuilder

/**
 * v1 → v2 : index des médias du SSD et favoris (galerie). L'état de transfert est conservé tel
 * quel ; l'index est pré-rempli avec les fichiers déjà copiés par l'app, pour que la galerie les
 * montre avant toute réindexation manuelle du SSD.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ssd_media` (
                `relativePath` TEXT NOT NULL COLLATE NOCASE,
                `displayName` TEXT NOT NULL,
                `mediaType` TEXT NOT NULL,
                `mimeType` TEXT NOT NULL,
                `sizeBytes` INTEGER NOT NULL,
                `captureEpochDay` INTEGER NOT NULL,
                `capturedAtEpochMillis` INTEGER,
                `lastModifiedEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`relativePath`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `favorites` (
                `key` TEXT NOT NULL,
                `addedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`key`)
            )
            """.trimIndent(),
        )

        db.query(
            "SELECT destinationPath, sizeBytes FROM transfer_state " +
                "WHERE status IN ('VERIFIED', 'DELETED') AND destinationPath IS NOT NULL",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val path = cursor.getString(0)
                val day = TimelineBuilder.parseDayFromPath(path) ?: continue
                val name = path.substringAfterLast('/')
                val type = MediaFileTypes.fromFileName(name) ?: continue
                val values = ContentValues().apply {
                    put("relativePath", path)
                    put("displayName", name)
                    put("mediaType", type.mediaType.name)
                    put("mimeType", type.mimeType)
                    put("sizeBytes", cursor.getLong(1))
                    put("captureEpochDay", day.toEpochDay())
                    putNull("capturedAtEpochMillis")
                    put("lastModifiedEpochMillis", 0L)
                }
                db.insert("ssd_media", SQLiteDatabase.CONFLICT_IGNORE, values)
            }
        }
    }
}
