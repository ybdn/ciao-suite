package dev.ybdn.ciaocloud.data.saf

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import dev.ybdn.ciaocloud.domain.repository.DestinationEntry
import dev.ybdn.ciaocloud.domain.repository.DestinationWriteResult
import dev.ybdn.ciaocloud.domain.repository.DestinationWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.Arrays
import java.util.zip.CRC32

/**
 * Écrit vers le SSD via Storage Access Framework, à partir de l'URI racine persistée par
 * l'utilisateur (voir `SettingsDataStore`).
 */
class SafDestinationWriter(
    private val context: Context,
    private val rootUriProvider: suspend () -> Uri?,
) : DestinationWriter {

    private val resolver get() = context.contentResolver

    // Lister un dossier via SAF coûte une requête au provider : on mémorise les dossiers déjà
    // résolus et leur contenu, pour ne pas relister un dossier jour à chaque fichier.
    private val cacheMutex = Mutex()
    private var cacheRootUri: Uri? = null
    private val directoryCache = HashMap<String, Uri>()

    /** Contenu des dossiers déjà listés, indexé par nom en minuscules (SSD insensibles à la casse). */
    private val entriesCache = HashMap<String, MutableMap<String, SafEntry>>()

    override suspend fun isDestinationAvailable(): Boolean = withContext(Dispatchers.IO) {
        val rootUri = rootUriProvider() ?: return@withContext false
        val hasPermission = resolver.persistedUriPermissions.any { it.uri == rootUri && it.isWritePermission }
        hasPermission && runCatching {
            DocumentFile.fromTreeUri(context, rootUri)?.let { it.isDirectory && it.canWrite() } ?: false
        }.getOrDefault(false)
    }

    override suspend fun destinationName(): String? = withContext(Dispatchers.IO) {
        val rootUri = rootUriProvider() ?: return@withContext null
        runCatching { DocumentFile.fromTreeUri(context, rootUri)?.name }.getOrNull()
    }

    override suspend fun phoneStorageRelativePath(): String? {
        val rootUri = rootUriProvider() ?: return null
        if (rootUri.authority != EXTERNAL_STORAGE_AUTHORITY) return null
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(rootUri) }.getOrNull() ?: return null
        if (treeDocumentId.substringBefore(':') != PRIMARY_VOLUME_ID) return null
        val path = treeDocumentId.substringAfter(':').trim('/')
        return if (path.isEmpty()) "" else "$path/"
    }

    /**
     * Un tree URI SAF n'a pas de chemin exploitable par `StatFs` : on retrouve le volume via
     * l'identifiant de document ("UUID:chemin" pour `ExternalStorageProvider`), puis son répertoire.
     */
    override suspend fun availableBytes(): Long? = withContext(Dispatchers.IO) {
        val rootUri = rootUriProvider() ?: return@withContext null
        if (rootUri.authority != EXTERNAL_STORAGE_AUTHORITY) return@withContext null
        runCatching {
            val volumeId = DocumentsContract.getTreeDocumentId(rootUri).substringBefore(':')
            val volume = context.getSystemService(StorageManager::class.java).storageVolumes.firstOrNull {
                if (volumeId == PRIMARY_VOLUME_ID) it.isPrimary else it.uuid.equals(volumeId, ignoreCase = true)
            }
            volume?.directory?.let { StatFs(it.path).availableBytes }
        }.getOrNull()
    }

    override suspend fun listExistingEntries(relativeDirPath: String): List<DestinationEntry> =
        withContext(Dispatchers.IO) {
            entries(relativeDirPath).map { DestinationEntry(it.name, it.sizeBytes, it.isDirectory) }
        }

    override suspend fun identicalContentChecksum(
        sourceUri: String,
        relativeDirPath: String,
        fileName: String,
        onProgress: (bytesRead: Long) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        val existingUri = findFileUri(relativeDirPath, fileName)
            ?: throw IOException("Fichier existant introuvable : $relativeDirPath/$fileName")

        val crc32 = CRC32()
        var bytesRead = 0L
        var lastReported = 0L
        openOriginal(sourceUri).use { source ->
            val existing = resolver.openInputStream(existingUri)
                ?: throw IOException("Impossible d'ouvrir le fichier existant : $fileName")
            existing.use {
                val sourceBuffer = ByteArray(BUFFER_SIZE)
                val existingBuffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    ensureActive()
                    val sourceRead = source.readFully(sourceBuffer)
                    val existingRead = existing.readFully(existingBuffer)
                    if (sourceRead != existingRead ||
                        !Arrays.equals(sourceBuffer, 0, sourceRead, existingBuffer, 0, existingRead)
                    ) {
                        return@withContext null
                    }
                    if (sourceRead == 0) break
                    crc32.update(sourceBuffer, 0, sourceRead)
                    bytesRead += sourceRead
                    if (bytesRead - lastReported >= PROGRESS_STEP_BYTES) {
                        lastReported = bytesRead
                        onProgress(bytesRead)
                    }
                }
            }
        }
        onProgress(bytesRead)
        crc32.value.toChecksumString()
    }

    override suspend fun writeFile(
        sourceUri: String,
        relativeDirPath: String,
        fileName: String,
        onProgress: (bytesCopied: Long) -> Unit,
    ): DestinationWriteResult = withContext(Dispatchers.IO) {
        val dirUri = ensureDirectory(relativeDirPath)
            ?: throw IOException("Impossible de créer le dossier de destination : $relativeDirPath")

        // octet-stream : sinon le provider peut ajouter ou changer l'extension selon le type MIME.
        val destinationUri = SafDocuments.createDocument(resolver, dirUri, OCTET_STREAM, fileName)
            ?: throw IOException("Impossible de créer le fichier de destination : $fileName")
        val writtenName = SafDocuments.displayName(resolver, destinationUri) ?: fileName

        try {
            val crc32 = CRC32()
            var bytesWritten = 0L
            var lastReported = 0L

            openOriginal(sourceUri).use { input ->
                val pfd = resolver.openFileDescriptor(destinationUri, "w")
                    ?: throw IOException("Impossible d'ouvrir le fichier de destination : $writtenName")
                ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        crc32.update(buffer, 0, read)
                        bytesWritten += read
                        if (bytesWritten - lastReported >= PROGRESS_STEP_BYTES) {
                            lastReported = bytesWritten
                            onProgress(bytesWritten)
                        }
                    }
                    output.flush()
                    // Garantit que les données sont sur le SSD (pas seulement en cache) avant vérification.
                    output.fd.sync()
                }
            }
            onProgress(bytesWritten)

            cacheMutex.withLock {
                entriesCache[relativeDirPath]?.put(
                    writtenName.lowercase(),
                    SafEntry(destinationUri, writtenName, bytesWritten, OCTET_STREAM, System.currentTimeMillis()),
                )
            }

            DestinationWriteResult(
                writtenRelativePath = "$relativeDirPath/$writtenName",
                writtenFileName = writtenName,
                bytesWritten = bytesWritten,
                checksum = crc32.value.toChecksumString(),
            )
        } catch (e: Throwable) {
            SafDocuments.deleteDocument(resolver, destinationUri)
            throw e
        }
    }

    override suspend fun readBackForVerification(
        relativeDirPath: String,
        fileName: String,
    ): DestinationWriteResult? = withContext(Dispatchers.IO) {
        val fileUri = findFileUri(relativeDirPath, fileName) ?: return@withContext null

        val crc32 = CRC32()
        var bytesRead = 0L
        val input = resolver.openInputStream(fileUri) ?: return@withContext null
        input.use {
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                crc32.update(buffer, 0, read)
                bytesRead += read
            }
        }

        DestinationWriteResult(
            writtenRelativePath = "$relativeDirPath/$fileName",
            writtenFileName = fileName,
            bytesWritten = bytesRead,
            checksum = crc32.value.toChecksumString(),
        )
    }

    /** setRequireOriginal : sans cela, les métadonnées de localisation sont retirées du flux. */
    private fun openOriginal(sourceUri: String): InputStream =
        resolver.openInputStream(MediaStore.setRequireOriginal(Uri.parse(sourceUri)))
            ?: throw IOException("Impossible d'ouvrir le fichier source : $sourceUri")

    private suspend fun findFileUri(relativeDirPath: String, fileName: String): Uri? =
        entries(relativeDirPath).firstOrNull { !it.isDirectory && it.name.equals(fileName, ignoreCase = true) }?.uri

    private suspend fun entries(relativeDirPath: String): Collection<SafEntry> {
        val dirUri = ensureDirectory(relativeDirPath)
            ?: throw IOException("Impossible de créer le dossier de destination : $relativeDirPath")
        return cacheMutex.withLock {
            entriesCache.getOrPut(relativeDirPath) {
                SafDocuments.listChildren(resolver, dirUri).associateByTo(HashMap()) { it.name.lowercase() }
            }.values.toList()
        }
    }

    /**
     * Résout (et crée si besoin) les segments de [relativeDirPath] ("DCIM/2025/04/21") sous la racine.
     * Un dossier existant est reconnu sans tenir compte de la casse : sur exFAT, créer `DCIM` à côté
     * d'un `dcim` existant produirait un dossier `DCIM (1)`.
     */
    private suspend fun ensureDirectory(relativeDirPath: String): Uri? = cacheMutex.withLock {
        val rootUri = rootUriProvider() ?: return@withLock null
        if (rootUri != cacheRootUri) {
            directoryCache.clear()
            entriesCache.clear()
            cacheRootUri = rootUri
        }

        directoryCache[relativeDirPath]?.let { cached ->
            if (SafDocuments.displayName(resolver, cached) != null) return@withLock cached
            // Dossier supprimé ou SSD rebranché entre-temps : on repart de la racine.
            directoryCache.remove(relativeDirPath)
            entriesCache.remove(relativeDirPath)
        }

        var current = runCatching { SafDocuments.rootDocumentUri(rootUri) }.getOrNull() ?: return@withLock null
        var createdLast = false
        for (segment in relativeDirPath.split('/').filter { it.isNotBlank() }) {
            val existing = if (createdLast) {
                null
            } else {
                runCatching { SafDocuments.listChildren(resolver, current) }.getOrNull()
                    ?.firstOrNull { it.isDirectory && it.name.equals(segment, ignoreCase = true) }
            }
            current = existing?.uri
                ?: SafDocuments.createDocument(resolver, current, DocumentsContract.Document.MIME_TYPE_DIR, segment)
                ?: return@withLock null
            createdLast = existing == null
        }
        directoryCache[relativeDirPath] = current
        // Dossier tout juste créé : vide, inutile de le lister.
        if (createdLast) entriesCache[relativeDirPath] = HashMap()
        current
    }

    /** Lit jusqu'à remplir [buffer] ou atteindre la fin du flux ; renvoie le nombre d'octets lus. */
    private fun InputStream.readFully(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private fun Long.toChecksumString(): String = toString(16).padStart(8, '0')

    private companion object {
        const val BUFFER_SIZE = 1024 * 1024
        const val PROGRESS_STEP_BYTES = 4L * 1024 * 1024
        const val OCTET_STREAM = "application/octet-stream"
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        const val PRIMARY_VOLUME_ID = "primary"
    }
}
