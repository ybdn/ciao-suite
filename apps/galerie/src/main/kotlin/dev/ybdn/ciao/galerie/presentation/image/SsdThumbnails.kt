package dev.ybdn.ciao.galerie.presentation.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import dev.ybdn.ciao.galerie.domain.model.MediaType
import dev.ybdn.ciao.galerie.domain.repository.SsdMediaBrowser
import dev.ybdn.ciao.galerie.domain.repository.SsdThumbnailCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import java.io.IOException
import kotlin.math.max

/** Vignette d'un média du SSD, identifié par son chemin relatif. */
data class SsdThumbnail(
    val relativePath: String,
    val mediaType: MediaType,
)

/**
 * Cache disque LRU des vignettes SSD, dans le stockage privé de l'app (hors cache système, qui
 * peut être purgé : les vignettes doivent rester visibles SSD débranché). Sa taille maximale est
 * lue à la première utilisation : un changement de réglage s'applique au prochain démarrage.
 */
class SsdThumbnailDiskCache(
    private val context: Context,
    scope: CoroutineScope,
    maxSizeBytesProvider: suspend () -> Long,
) : SsdThumbnailCache {

    private val diskCacheDeferred = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
        DiskCache.Builder()
            .directory(context.noBackupFilesDir.resolve(DIRECTORY).toOkioPath())
            .maxSizeBytes(maxSizeBytesProvider())
            .build()
    }

    suspend fun diskCache(): DiskCache = diskCacheDeferred.await()

    override suspend fun seedFromPhone(phoneUri: String, relativePath: String) {
        try {
            withContext(Dispatchers.IO) {
                if (diskCache().openSnapshot(key(relativePath))?.use { true } == true) return@withContext
                val bitmap = context.contentResolver.loadThumbnail(Uri.parse(phoneUri), Size(SIZE_PX, SIZE_PX), null)
                write(relativePath, bitmap)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best effort : la vignette sera générée depuis le SSD au prochain affichage.
        }
    }

    override suspend fun remove(relativePath: String) {
        withContext(Dispatchers.IO) { diskCache().remove(key(relativePath)) }
    }

    override suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) { diskCache().size }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { diskCache().clear() }
    }

    suspend fun write(relativePath: String, bitmap: Bitmap) {
        val diskCache = diskCache()
        val editor = diskCache.openEditor(key(relativePath)) ?: return
        try {
            diskCache.fileSystem.write(editor.data) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream())
            }
            editor.commit()
        } catch (e: Exception) {
            editor.abort()
        }
    }

    companion object {
        const val SIZE_PX = 512
        private const val JPEG_QUALITY = 85
        private const val DIRECTORY = "ssd_thumbnails"

        /** Casse ignorée : les SSD (exFAT) ne la distinguent pas. */
        fun key(relativePath: String): String = "ssd:${relativePath.lowercase()}"
    }
}

/**
 * Vignette SSD : depuis le cache disque si présente, sinon décodée en sous-échantillonnage depuis
 * le SSD (image) ou extraite (vidéo), puis mise en cache. SSD débranché et hors cache : échec, la
 * case reste neutre.
 */
class SsdThumbnailFetcher(
    private val data: SsdThumbnail,
    private val context: Context,
    private val cache: SsdThumbnailDiskCache,
    private val browser: SsdMediaBrowser,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val diskCache = cache.diskCache()
        diskCache.openSnapshot(SsdThumbnailDiskCache.key(data.relativePath))?.let { snapshot ->
            return SourceFetchResult(
                source = ImageSource(snapshot.data, diskCache.fileSystem, closeable = snapshot),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK,
            )
        }

        val uri = browser.documentUri(data.relativePath)?.let(Uri::parse)
            ?: throw IOException("SSD indisponible : ${data.relativePath}")
        val bitmap = withContext(Dispatchers.IO) {
            when (data.mediaType) {
                MediaType.PHOTO -> decodeImage(uri)
                MediaType.VIDEO -> extractVideoFrame(uri)
            }
        }
        withContext(Dispatchers.IO) { cache.write(data.relativePath, bitmap) }
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun decodeImage(uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longestSide = max(info.size.width, info.size.height)
            if (longestSide > SsdThumbnailDiskCache.SIZE_PX) {
                val scale = SsdThumbnailDiskCache.SIZE_PX.toFloat() / longestSide
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    private fun extractVideoFrame(uri: Uri): Bitmap {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            return retriever.getScaledFrameAtTime(
                VIDEO_FRAME_TIME_US,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                SsdThumbnailDiskCache.SIZE_PX,
                SsdThumbnailDiskCache.SIZE_PX,
            ) ?: throw IOException("Aucune image extraite de la vidéo")
        } finally {
            retriever.release()
        }
    }

    class Factory(
        private val context: Context,
        private val cache: SsdThumbnailDiskCache,
        private val browser: SsdMediaBrowser,
    ) : Fetcher.Factory<SsdThumbnail> {
        override fun create(data: SsdThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            SsdThumbnailFetcher(data, context, cache, browser)
    }

    class Key : Keyer<SsdThumbnail> {
        override fun key(data: SsdThumbnail, options: Options): String = SsdThumbnailDiskCache.key(data.relativePath)
    }

    private companion object {
        const val VIDEO_FRAME_TIME_US = 1_000_000L
    }
}
