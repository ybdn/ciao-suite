package dev.ybdn.ciaocloud.data.saf

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.documentfile.provider.DocumentFile
import dev.ybdn.ciaocloud.domain.repository.DestinationWriteResult
import dev.ybdn.ciaocloud.domain.repository.DestinationWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream

/**
 * Écrit vers le SSD via Storage Access Framework / `DocumentFile`, à partir de l'URI racine
 * persistée par l'utilisateur (voir `SettingsDataStore`).
 */
class SafDestinationWriter(
    private val context: Context,
    private val rootUriProvider: suspend () -> Uri?,
) : DestinationWriter {

    override suspend fun isDestinationAvailable(): Boolean = withContext(Dispatchers.IO) {
        rootDocument()?.let { it.exists() && it.canWrite() } ?: false
    }

    override suspend fun availableBytes(): Long? = withContext(Dispatchers.IO) {
        val rootUri = rootUriProvider() ?: return@withContext null
        val path = rootUri.path ?: return@withContext null
        runCatching { StatFs(path).availableBytes }.getOrNull()
    }

    override suspend fun listExistingFileNames(relativeDirPath: String): Set<String> =
        withContext(Dispatchers.IO) {
            val dir = ensureDirectory(relativeDirPath) ?: return@withContext emptySet()
            dir.listFiles().mapNotNull { it.name }.toSet()
        }

    override suspend fun writeFile(
        sourceUri: String,
        sourceSizeBytes: Long,
        relativeDirPath: String,
        fileName: String,
    ): DestinationWriteResult = withContext(Dispatchers.IO) {
        val dir = ensureDirectory(relativeDirPath)
            ?: error("Impossible de créer le dossier de destination : $relativeDirPath")

        val mimeType = context.contentResolver.getType(Uri.parse(sourceUri)) ?: "application/octet-stream"
        val destinationFile = dir.createFile(mimeType, fileName)
            ?: error("Impossible de créer le fichier de destination : $fileName")

        val crc32 = CRC32()
        var bytesWritten = 0L

        context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
            context.contentResolver.openOutputStream(destinationFile.uri)?.use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (input.read(buffer).also { read = it } >= 0) {
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    crc32.update(buffer, 0, read)
                    bytesWritten += read
                }
            } ?: error("Impossible d'ouvrir le fichier de destination en écriture : $fileName")
        } ?: error("Impossible d'ouvrir le fichier source : $sourceUri")

        DestinationWriteResult(
            writtenRelativePath = "$relativeDirPath/$fileName",
            bytesWritten = bytesWritten,
            checksum = crc32.value.toString(16),
        )
    }

    override suspend fun readBackForVerification(
        relativeDirPath: String,
        fileName: String,
    ): DestinationWriteResult? = withContext(Dispatchers.IO) {
        val dir = ensureDirectory(relativeDirPath) ?: return@withContext null
        val file = dir.findFile(fileName) ?: return@withContext null

        val crc32 = CRC32()
        var bytesRead = 0L

        val stream = context.contentResolver.openInputStream(file.uri) ?: return@withContext null
        CheckedInputStream(stream, crc32).use { checkedStream ->
            val buffer = ByteArray(BUFFER_SIZE)
            var read: Int
            while (checkedStream.read(buffer).also { read = it } >= 0) {
                bytesRead += read
            }
        }

        DestinationWriteResult(
            writtenRelativePath = "$relativeDirPath/$fileName",
            bytesWritten = bytesRead,
            checksum = crc32.value.toString(16),
        )
    }

    private suspend fun rootDocument(): DocumentFile? {
        val rootUri = rootUriProvider() ?: return null
        return DocumentFile.fromTreeUri(context, rootUri)
    }

    /** Crée récursivement les segments de [relativeDirPath] ("DCIM/2025/04/21") sous la racine. */
    private suspend fun ensureDirectory(relativeDirPath: String): DocumentFile? {
        var current = rootDocument() ?: return null
        for (segment in relativeDirPath.split('/').filter { it.isNotBlank() }) {
            current = current.findFile(segment)?.takeIf { it.isDirectory }
                ?: current.createDirectory(segment)
                ?: return null
        }
        return current
    }

    private companion object {
        const val BUFFER_SIZE = 8 * 1024
    }
}
