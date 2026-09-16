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
import java.io.IOException
import java.io.RandomAccessFile
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
 *
 * `ExifInterface` encode les textes en US-ASCII (accents remplacés par « ? ») : un texte non ASCII est
 * d'abord écrit sous forme d'un gabarit de même longueur en octets, puis remplacé en place par son
 * encodage UTF-8, à l'emplacement exact de la valeur relu par `getAttributeRange`.
 */
class ExifInterfaceMetadataWriter : MetadataWriter {

    override suspend fun readOrientation(path: String): Int = withContext(Dispatchers.IO) {
        ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }

    override suspend fun apply(path: String, plan: ExifWritePlan) = withContext(Dispatchers.IO) {
        if (plan.isEmpty) return@withContext
        val utf8Values = plan.set.filterValues { value -> value.any { it.code > ASCII_MAX } }
            .mapValues { (_, value) -> value.toByteArray(Charsets.UTF_8) }
        val exif = ExifInterface(path)
        plan.set.forEach { (tag, value) ->
            exif.setAttribute(tag, utf8Values[tag]?.let { PLACEHOLDER.toString().repeat(it.size) } ?: value)
        }
        plan.remove.forEach { tag -> exif.setAttribute(tag, null) }
        exif.saveAttributes()
        if (utf8Values.isNotEmpty()) writeUtf8Values(path, utf8Values)
    }

    private fun writeUtf8Values(path: String, values: Map<String, ByteArray>) {
        val saved = ExifInterface(path)
        val ranges = values.mapValues { (tag, bytes) ->
            val range = saved.getAttributeRange(tag) ?: throw IOException("Balise $tag introuvable après écriture")
            // Longueur relue : texte + NUL final.
            if (range[1] < bytes.size) throw IOException("Emplacement de $tag trop court")
            range[0]
        }
        RandomAccessFile(path, "rw").use { file ->
            values.forEach { (tag, bytes) ->
                val offset = ranges.getValue(tag)
                val current = ByteArray(bytes.size)
                file.seek(offset)
                file.readFully(current)
                if (current.any { it != PLACEHOLDER.code.toByte() }) throw IOException("Gabarit de $tag introuvable")
                file.seek(offset)
                file.write(bytes)
            }
            file.fd.sync()
        }
    }

    private companion object {
        const val ASCII_MAX = 0x7F
        const val PLACEHOLDER = '#'
    }
}
