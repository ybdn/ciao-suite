package dev.ybdn.ciao.galerie.data.edit

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import dev.ybdn.ciao.galerie.data.mediastore.MediaStoreCollections
import dev.ybdn.ciao.galerie.domain.model.FileFingerprint
import dev.ybdn.ciao.galerie.domain.repository.PhoneMediaWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Écritures MediaStore sur le volume interne. Un remplacement passe par une sauvegarde complète de
 * l'original (écrite puis renommée, donc jamais partielle) : l'écriture `"wt"` n'étant pas atomique,
 * l'original est réécrit depuis la sauvegarde si la relecture diffère.
 */
class MediaStorePhoneMediaWriter(
    private val context: Context,
) : PhoneMediaWriter {

    private val resolver get() = context.contentResolver

    override suspend fun fingerprint(uri: String): FileFingerprint? = withContext(Dispatchers.IO) {
        try {
            context.openOriginal(Uri.parse(uri)).use { it.fingerprint() }
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    override suspend fun existingNames(relativePath: String): Set<String> = withContext(Dispatchers.IO) {
        val args = Bundle().apply {
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.RELATIVE_PATH} = ?")
            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(relativePath))
            // Les entrées en attente ou à la corbeille occupent aussi leur nom.
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }
        buildSet {
            resolver.query(MediaStoreCollections.FILES, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), args, null)?.use { cursor ->
                while (cursor.moveToNext()) cursor.getString(0)?.let(::add)
            }
        }
    }

    override suspend fun createPending(
        relativePath: String,
        displayName: String,
        mimeType: String,
        dateTakenEpochMillis: Long?,
    ): String = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            dateTakenEpochMillis?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
        }
        val uri = resolver.insert(MediaStoreCollections.IMAGES, values)
            ?: throw IOException("Impossible de créer $relativePath$displayName")
        val created = queryString(uri, MediaStore.MediaColumns.DISPLAY_NAME, includePending = true)
        if (created != null && created != displayName) {
            resolver.delete(uri, null, null)
            throw IOException("Nom refusé par MediaStore : $displayName → $created")
        }
        uri.toString()
    }

    override suspend fun writePending(uri: String, workPath: String, expected: FileFingerprint) = withContext(Dispatchers.IO) {
        val target = Uri.parse(uri)
        writeFile(target, File(workPath))
        verify(target, expected)
    }

    override suspend fun publish(uri: String): Long = withContext(Dispatchers.IO) {
        val target = Uri.parse(uri)
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        if (resolver.update(target, values, null, null) != 1) throw IOException("Publication impossible : $uri")
        ContentUris.parseId(target)
    }

    override suspend fun deleteIfPending(uri: String) = withContext(Dispatchers.IO) {
        val target = Uri.parse(uri)
        if (queryString(target, MediaStore.MediaColumns.IS_PENDING, includePending = true) == "1") {
            resolver.delete(target, null, null)
        }
        Unit
    }

    override suspend fun replace(uri: String, workPath: String, expected: FileFingerprint, backupPath: String) =
        withContext(Dispatchers.IO) {
            val target = Uri.parse(uri)
            val backup = File(backupPath)
            val temporary = File("$backupPath.tmp")
            context.openOriginal(target).use { input ->
                temporary.outputStream().use { output ->
                    input.copyWithFingerprint(output)
                    output.fd.sync()
                }
            }
            if (!temporary.renameTo(backup)) throw IOException("Sauvegarde de l'original impossible")

            try {
                writeFile(target, File(workPath))
                verify(target, expected)
            } catch (e: Exception) {
                // Si la restauration échoue, la sauvegarde est conservée pour la reprise.
                restore(target, backup)
                backup.delete()
                throw e
            }
            backup.delete()
            Unit
        }

    override suspend fun backupExists(backupPath: String): Boolean = withContext(Dispatchers.IO) { File(backupPath).isFile }

    override suspend fun restoreFromBackup(uri: String, backupPath: String) = withContext(Dispatchers.IO) {
        restore(Uri.parse(uri), File(backupPath))
    }

    override suspend fun deleteBackup(backupPath: String) {
        withContext(Dispatchers.IO) {
            File(backupPath).delete()
            File("$backupPath.tmp").delete()
        }
    }

    private fun restore(target: Uri, backup: File) {
        val expected = backup.inputStream().use { it.fingerprint() }
        writeFile(target, backup)
        verify(target, expected)
    }

    private fun writeFile(target: Uri, source: File) {
        val descriptor = resolver.openFileDescriptor(target, "wt") ?: throw IOException("Écriture impossible : $target")
        descriptor.use { pfd ->
            java.io.FileOutputStream(pfd.fileDescriptor).use { output ->
                source.inputStream().use { input -> input.copyWithFingerprint(output) }
                output.flush()
                pfd.fileDescriptor.sync()
            }
        }
    }

    private fun verify(target: Uri, expected: FileFingerprint) {
        val actual = context.openOriginal(target).use { it.fingerprint() }
        if (actual != expected) throw IOException("Relecture différente après écriture ($actual ≠ $expected)")
    }

    private fun queryString(uri: Uri, column: String, includePending: Boolean): String? {
        val args = Bundle().apply {
            if (includePending) putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        }
        return resolver.query(uri, arrayOf(column), args, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }
}
