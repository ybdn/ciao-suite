package dev.ybdn.ciaocloud.data.saf

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import dev.ybdn.ciaocloud.domain.repository.DestinationWriteResult
import dev.ybdn.ciaocloud.domain.repository.DestinationWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.zip.CRC32

/**
 * Écrit vers le SSD via Storage Access Framework / `DocumentFile`, à partir de l'URI racine
 * persistée par l'utilisateur (voir `SettingsDataStore`).
 */
class SafDestinationWriter(
    private val context: Context,
    private val rootUriProvider: suspend () -> Uri?,
) : DestinationWriter {

    private val resolver get() = context.contentResolver

    // Lister un dossier via SAF coûte une requête au provider : on mémorise les dossiers déjà
    // résolus et l'URI des fichiers écrits, pour ne pas relister un dossier jour à chaque fichier.
    private val cacheMutex = Mutex()
    private var cacheRootUri: Uri? = null
    private val directoryCache = HashMap<String, DocumentFile>()
    private val fileNamesCache = HashMap<String, MutableSet<String>>()
    private val writtenFileUris = HashMap<String, Uri>()

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

    override suspend fun listExistingFileNames(relativeDirPath: String): Set<String> =
        withContext(Dispatchers.IO) {
            val dir = ensureDirectory(relativeDirPath)
                ?: throw IOException("Impossible de créer le dossier de destination : $relativeDirPath")
            cacheMutex.withLock {
                fileNamesCache.getOrPut(relativeDirPath) {
                    dir.listFiles().mapNotNullTo(HashSet()) { it.name }
                }.toSet()
            }
        }

    override suspend fun writeFile(
        sourceUri: String,
        relativeDirPath: String,
        fileName: String,
        onProgress: (bytesCopied: Long) -> Unit,
    ): DestinationWriteResult = withContext(Dispatchers.IO) {
        val dir = ensureDirectory(relativeDirPath)
            ?: throw IOException("Impossible de créer le dossier de destination : $relativeDirPath")

        // octet-stream : sinon le provider peut ajouter ou changer l'extension selon le type MIME.
        val destination = dir.createFile(OCTET_STREAM, fileName)
            ?: throw IOException("Impossible de créer le fichier de destination : $fileName")
        val writtenName = destination.name ?: fileName

        try {
            val crc32 = CRC32()
            var bytesWritten = 0L
            var lastReported = 0L

            // setRequireOriginal : sans cela, les métadonnées de localisation sont retirées du flux.
            val originalUri = MediaStore.setRequireOriginal(Uri.parse(sourceUri))
            val input = resolver.openInputStream(originalUri)
                ?: throw IOException("Impossible d'ouvrir le fichier source : $sourceUri")

            input.use {
                val pfd = resolver.openFileDescriptor(destination.uri, "w")
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

            val relativePath = "$relativeDirPath/$writtenName"
            cacheMutex.withLock {
                writtenFileUris[relativePath] = destination.uri
                fileNamesCache[relativeDirPath]?.add(writtenName)
            }

            DestinationWriteResult(
                writtenRelativePath = relativePath,
                writtenFileName = writtenName,
                bytesWritten = bytesWritten,
                checksum = crc32.value.toChecksumString(),
            )
        } catch (e: Throwable) {
            runCatching { destination.delete() }
            throw e
        }
    }

    override suspend fun readBackForVerification(
        relativeDirPath: String,
        fileName: String,
    ): DestinationWriteResult? = withContext(Dispatchers.IO) {
        val relativePath = "$relativeDirPath/$fileName"
        val fileUri = cacheMutex.withLock { writtenFileUris.remove(relativePath) }
            ?: ensureDirectory(relativeDirPath)?.findFile(fileName)?.uri
            ?: return@withContext null

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
            writtenRelativePath = relativePath,
            writtenFileName = fileName,
            bytesWritten = bytesRead,
            checksum = crc32.value.toChecksumString(),
        )
    }

    /** Crée récursivement les segments de [relativeDirPath] ("DCIM/2025/04/21") sous la racine. */
    private suspend fun ensureDirectory(relativeDirPath: String): DocumentFile? = cacheMutex.withLock {
        val rootUri = rootUriProvider() ?: return@withLock null
        if (rootUri != cacheRootUri) {
            directoryCache.clear()
            fileNamesCache.clear()
            writtenFileUris.clear()
            cacheRootUri = rootUri
        }

        directoryCache[relativeDirPath]?.let { cached ->
            if (cached.exists()) return@withLock cached
            // Dossier supprimé ou SSD rebranché entre-temps : on repart de la racine.
            directoryCache.remove(relativeDirPath)
            fileNamesCache.remove(relativeDirPath)
        }

        var current = DocumentFile.fromTreeUri(context, rootUri) ?: return@withLock null
        for (segment in relativeDirPath.split('/').filter { it.isNotBlank() }) {
            current = current.findFile(segment)?.takeIf { it.isDirectory }
                ?: current.createDirectory(segment)
                ?: return@withLock null
        }
        directoryCache[relativeDirPath] = current
        current
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
