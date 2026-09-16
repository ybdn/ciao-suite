package dev.ybdn.ciaocloud.presentation.gallery

import android.content.Context
import coil3.request.ImageRequest
import dev.ybdn.ciaocloud.presentation.image.PhoneThumbnail
import dev.ybdn.ciaocloud.presentation.image.SsdThumbnail
import dev.ybdn.ciaocloud.domain.model.GalleryItem

/**
 * Requêtes d'images de la galerie. La vignette a une clé de cache mémoire explicite, réutilisée
 * comme aperçu par la visionneuse pendant le chargement de l'original.
 */
object GalleryImages {

    /** Inclut la date de modification : une photo remplacée (édition) n'affiche pas l'ancienne image. */
    fun thumbnailCacheKey(item: GalleryItem): String = "thumbnail:${item.key}:${modificationStamp(item)}"

    private fun modificationStamp(item: GalleryItem): Long =
        item.phone?.dateModifiedEpochMillis ?: item.ssd?.lastModifiedEpochMillis ?: 0

    fun thumbnailRequest(context: Context, item: GalleryItem): ImageRequest {
        val phone = item.phone
        val data: Any? = when {
            phone != null -> PhoneThumbnail(phone.uri, phone.dateModifiedEpochMillis)
            else -> item.ssd?.let { SsdThumbnail(it.relativePath, it.mediaType) }
        }
        return ImageRequest.Builder(context)
            .data(data)
            .memoryCacheKey(thumbnailCacheKey(item))
            .build()
    }

    /** Original en pleine résolution depuis [originalUri] (téléphone ou SSD). */
    fun originalRequest(context: Context, item: GalleryItem, originalUri: String): ImageRequest =
        ImageRequest.Builder(context)
            .data(originalUri)
            .memoryCacheKey("original:$originalUri:${modificationStamp(item)}")
            .placeholderMemoryCacheKey(thumbnailCacheKey(item))
            .build()
}
