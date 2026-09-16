package dev.ybdn.ciaocloud.data.share

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.repository.ShareableMedia
import dev.ybdn.ciaocloud.domain.repository.ShareableMediaProvider
import dev.ybdn.ciaocloud.domain.repository.SsdMediaBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * URI de partage. Téléphone : URI MediaStore, lisible par toute app à qui on accorde la lecture.
 * SSD : copie temporaire exposée par `FileProvider`. Une URI de document SAF n'est pas lue par
 * toutes les apps, et un échec de lecture côté destinataire est indétectable : la copie garantit
 * un partage qui fonctionne. Les copies sont effacées au lancement suivant.
 */
class FileProviderShareableMediaProvider(
    private val context: Context,
    private val ssdMediaBrowser: SsdMediaBrowser,
) : ShareableMediaProvider {

    private val shareDirectory get() = File(context.cacheDir, SHARE_DIRECTORY)

    override suspend fun prepare(items: List<GalleryItem>): List<ShareableMedia>? = withContext(Dispatchers.IO) {
        val batchDirectory = File(shareDirectory, System.currentTimeMillis().toString()).apply { mkdirs() }
        items.mapIndexed { index, item ->
            val phone = item.phone
            // Les URI content:// se partagent telles quelles ; une URI file:// (pièce jointe ouverte
            // depuis une autre app) est interdite hors du processus et doit être copiée.
            if (phone != null && phone.uri.startsWith("content://")) {
                return@mapIndexed ShareableMedia(phone.uri, phone.mimeType)
            }
            val sourceUri = phone?.uri
                ?: item.ssd?.let { ssdMediaBrowser.documentUri(it.relativePath) }
                ?: return@withContext null
            // Un sous-dossier par élément : deux fichiers de même nom (jours différents) ne s'écrasent pas.
            val target = File(File(batchDirectory, index.toString()).apply { mkdirs() }, item.displayName)
            context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return@withContext null
            ShareableMedia(
                uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target).toString(),
                mimeType = item.mimeType,
            )
        }
    }

    override suspend fun clearTemporaryCopies() {
        withContext(Dispatchers.IO) { shareDirectory.deleteRecursively() }
    }

    private companion object {
        const val SHARE_DIRECTORY = "shared"
        const val BUFFER_SIZE = 1024 * 1024
    }
}
