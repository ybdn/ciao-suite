package dev.ybdn.ciaocloud.data.saf

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import dev.ybdn.ciaocloud.data.edit.copyWithFingerprint
import dev.ybdn.ciaocloud.data.edit.fingerprint
import dev.ybdn.ciaocloud.domain.model.FileFingerprint
import dev.ybdn.ciaocloud.domain.repository.SsdMediaWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Primitives d'écriture sur le SSD via SAF. Les documents sont créés en `application/octet-stream`
 * pour que le provider ne change pas l'extension (`IMG.jpg.ciao-new` reste tel quel).
 */
class SafSsdMediaWriter(
    private val context: Context,
    private val rootUriProvider: suspend () -> Uri?,
) : SsdMediaWriter {

    private val resolver get() = context.contentResolver

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val rootUri = rootUriProvider() ?: return@withContext false
        resolver.persistedUriPermissions.any { it.uri == rootUri && it.isWritePermission } &&
            runCatching { SafDocuments.displayName(resolver, SafDocuments.rootDocumentUri(rootUri)) != null }.getOrDefault(false)
    }

    override suspend fun listNames(relativeDir: String): List<String>? = withContext(Dispatchers.IO) {
        val directory = documentUri(relativeDir) ?: return@withContext null
        runCatching { SafDocuments.listChildren(resolver, directory).map { it.name } }.getOrNull()
    }

    override suspend fun fingerprint(relativePath: String): FileFingerprint? = withContext(Dispatchers.IO) {
        val uri = documentUri(relativePath) ?: return@withContext null
        try {
            resolver.openInputStream(uri)?.use { it.fingerprint() }
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    override suspend fun exists(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val uri = documentUri(relativePath) ?: return@withContext false
        SafDocuments.displayName(resolver, uri) != null
    }

    override suspend fun write(relativePath: String, workPath: String, expected: FileFingerprint) = withContext(Dispatchers.IO) {
        val name = relativePath.substringAfterLast('/')
        val directory = documentUri(relativePath.substringBeforeLast('/'))
            ?: throw IOException("Dossier introuvable sur le SSD : $relativePath")
        val created = SafDocuments.createDocument(resolver, directory, OCTET_STREAM, name)
            ?: throw IOException("Impossible de créer $relativePath")
        try {
            val createdName = SafDocuments.displayName(resolver, created)
            if (createdName != name) throw IOException("Nom refusé par le SSD : $name → $createdName")
            val descriptor = resolver.openFileDescriptor(created, "w") ?: throw IOException("Écriture impossible : $relativePath")
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                File(workPath).inputStream().use { input -> input.copyWithFingerprint(output) }
                output.flush()
                // Données sur le SSD (pas seulement en cache) avant la relecture.
                output.fd.sync()
            }
            val actual = resolver.openInputStream(created)?.use { it.fingerprint() }
            if (actual != expected) throw IOException("Relecture différente sur le SSD ($actual ≠ $expected)")
        } catch (e: Exception) {
            SafDocuments.deleteDocument(resolver, created)
            throw e
        }
        Unit
    }

    override suspend fun rename(relativePath: String, newName: String) = withContext(Dispatchers.IO) {
        val uri = documentUri(relativePath) ?: throw IOException("Introuvable sur le SSD : $relativePath")
        val renamed = try {
            DocumentsContract.renameDocument(resolver, uri, newName) ?: uri
        } catch (e: Exception) {
            throw IOException("Renommage impossible : $relativePath → $newName", e)
        }
        val actualName = SafDocuments.displayName(resolver, renamed)
        if (actualName != newName) {
            // Le provider a ajusté le nom (ex. « (1) ») : on revient au nom d'origine.
            runCatching { DocumentsContract.renameDocument(resolver, renamed, relativePath.substringAfterLast('/')) }
            throw IOException("Nom refusé par le SSD : $newName → $actualName")
        }
        Unit
    }

    override suspend fun delete(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val uri = documentUri(relativePath) ?: return@withContext true
        if (SafDocuments.displayName(resolver, uri) == null) return@withContext true
        SafDocuments.deleteDocument(resolver, uri) || SafDocuments.displayName(resolver, uri) == null
    }

    private suspend fun documentUri(relativePath: String): Uri? {
        val rootUri = rootUriProvider() ?: return null
        return SafDocuments.resolve(resolver, rootUri, relativePath)
    }

    private companion object {
        const val OCTET_STREAM = "application/octet-stream"
    }
}
