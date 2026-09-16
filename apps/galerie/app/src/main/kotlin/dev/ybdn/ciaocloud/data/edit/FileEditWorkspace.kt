package dev.ybdn.ciaocloud.data.edit

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dev.ybdn.ciaocloud.domain.model.FileFingerprint
import dev.ybdn.ciaocloud.domain.repository.EditWorkspace
import dev.ybdn.ciaocloud.domain.repository.MetadataWriter
import dev.ybdn.ciaocloud.domain.util.ExifWritePlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/**
 * Fichiers de travail dans `filesDir/edit-work/` (stockage interne de l'app, jamais purgé par le
 * système pendant une écriture, contrairement au cache).
 */
class FileEditWorkspace(
    private val context: Context,
) : EditWorkspace {

    private val directory get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    override suspend fun newWorkFile(extension: String): String = withContext(Dispatchers.IO) {
        File(directory, "${UUID.randomUUID()}.$extension").path
    }

    override suspend fun copyOriginal(sourceUri: String, path: String) = withContext(Dispatchers.IO) {
        context.openOriginal(Uri.parse(sourceUri)).use { input ->
            File(path).outputStream().use { output ->
                input.copyWithFingerprint(output)
                output.fd.sync()
            }
        }
        Unit
    }

    override suspend fun fingerprint(path: String): FileFingerprint? = withContext(Dispatchers.IO) {
        try {
            File(path).inputStream().use { it.fingerprint() }
        } catch (e: FileNotFoundException) {
            null
        }
    }

    override suspend fun delete(path: String) {
        withContext(Dispatchers.IO) { File(path).delete() }
    }

    /**
     * Fichiers de travail abandonnés (app tuée pendant un enregistrement). Seuls les fichiers anciens
     * sont supprimés : un enregistrement peut avoir commencé juste après le lancement.
     */
    suspend fun deleteStaleFiles() {
        withContext(Dispatchers.IO) {
            val limit = System.currentTimeMillis() - STALE_AFTER_MILLIS
            directory.listFiles().orEmpty().filter { it.lastModified() < limit }.forEach { it.delete() }
        }
    }

    private companion object {
        const val DIRECTORY = "edit-work"
        const val STALE_AFTER_MILLIS = 60L * 60 * 1000
    }
}

/**
 * Écriture EXIF via `ExifInterface.saveAttributes` (JPEG, PNG, WebP). Seul le bloc EXIF est
 * réécrit : les données d'image qui suivent (carte de gain, vidéo de photo animée) sont recopiées.
 */
class ExifInterfaceMetadataWriter : MetadataWriter {

    override suspend fun readOrientation(path: String): Int = withContext(Dispatchers.IO) {
        ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }

    override suspend fun apply(path: String, plan: ExifWritePlan) = withContext(Dispatchers.IO) {
        if (plan.isEmpty) return@withContext
        val exif = ExifInterface(path)
        plan.set.forEach { (tag, value) -> exif.setAttribute(tag, value) }
        plan.remove.forEach { tag -> exif.setAttribute(tag, null) }
        exif.saveAttributes()
    }
}
