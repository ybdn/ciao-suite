package dev.ybdn.ciaocloud.data.share

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.core.content.FileProvider
import dev.ybdn.ciaocloud.domain.repository.MetadataStripper
import dev.ybdn.ciaocloud.domain.repository.ShareableMedia
import dev.ybdn.ciaocloud.domain.repository.ShareableMediaProvider
import dev.ybdn.ciaocloud.domain.repository.StripFailureReason
import dev.ybdn.ciaocloud.domain.repository.StripOutcome
import dev.ybdn.ciaocloud.domain.util.StripTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Copies temporaires de partage dans `cacheDir/shared/<lot>/<index>/`, exposées par `FileProvider`.
 * Un sous-dossier par élément : deux fichiers de même nom (jours différents) ne s'écrasent pas. Les
 * copies sont effacées au lancement suivant.
 */
class FileProviderShareableMediaProvider(
    private val context: Context,
    private val metadataStripper: MetadataStripper,
) : ShareableMediaProvider {

    private val shareDirectory get() = File(context.cacheDir, SHARE_DIRECTORY)

    override suspend fun availableBytes(): Long? = withContext(Dispatchers.IO) {
        runCatching { StatFs(context.cacheDir.path).availableBytes }.getOrNull()
    }

    override suspend fun createBatch(): String = withContext(Dispatchers.IO) {
        "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}".also { File(shareDirectory, it).mkdirs() }
    }

    override suspend fun copy(
        batchId: String,
        index: Int,
        sourceUri: String,
        fileName: String,
        mimeType: String,
    ): ShareableMedia? = withContext(Dispatchers.IO) {
        val target = targetFile(batchId, index, fileName)
        val input = runCatching { context.contentResolver.openInputStream(Uri.parse(sourceUri)) }.getOrNull()
            ?: return@withContext null
        input.use {
            target.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
        }
        ShareableMedia(uriFor(target), mimeType)
    }

    override suspend fun copyWithoutMetadata(
        batchId: String,
        index: Int,
        sourceUri: String,
        target: StripTarget,
    ): StripOutcome = withContext(Dispatchers.IO) {
        val file = targetFile(batchId, index, target.fileName)
        when (val failure = metadataStripper.strip(sourceUri, target, file.path)) {
            null -> if (file.isFile) {
                StripOutcome.Success(ShareableMedia(uriFor(file), target.mimeType))
            } else {
                StripOutcome.Failure(StripFailureReason.WRITE_FAILED)
            }
            else -> {
                file.delete()
                StripOutcome.Failure(failure)
            }
        }
    }

    override suspend fun deleteBatch(batchId: String) {
        withContext(Dispatchers.IO) { File(shareDirectory, batchId).deleteRecursively() }
    }

    override suspend fun clearTemporaryCopies() {
        withContext(Dispatchers.IO) { shareDirectory.deleteRecursively() }
    }

    private fun targetFile(batchId: String, index: Int, fileName: String): File =
        File(File(File(shareDirectory, batchId), index.toString()).apply { mkdirs() }, fileName)

    private fun uriFor(file: File): String =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()

    private companion object {
        const val SHARE_DIRECTORY = "shared"
        const val BUFFER_SIZE = 1024 * 1024
    }
}
