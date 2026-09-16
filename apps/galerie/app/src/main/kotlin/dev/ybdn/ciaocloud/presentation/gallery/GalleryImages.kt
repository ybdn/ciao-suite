package dev.ybdn.ciaocloud.presentation.gallery

import android.content.Context
import coil3.request.ImageRequest
import dev.ybdn.ciaocloud.presentation.image.PhoneThumbnail
import dev.ybdn.ciaocloud.domain.model.GalleryItem

/**
 * Requêtes d'images de la galerie. La vignette a une clé de cache mémoire explicite, réutilisée
 * comme aperçu par la visionneuse pendant le chargement de l'original.
 */
object GalleryImages {

    fun thumbnailCacheKey(item: GalleryItem): String = "thumbnail:${item.key}"

    fun thumbnailRequest(context: Context, item: GalleryItem): ImageRequest {
        val phone = item.phone
        val data: Any? = when {
            phone != null -> PhoneThumbnail(phone.uri, phone.dateModifiedEpochMillis)
            else -> null
        }
        return ImageRequest.Builder(context)
            .data(data)
            .memoryCacheKey(thumbnailCacheKey(item))
            .build()
    }

    /** Original en pleine résolution, ou null s'il n'est pas accessible (SSD débranché). */
    fun originalRequest(context: Context, item: GalleryItem): ImageRequest? {
        val phone = item.phone ?: return null
        return ImageRequest.Builder(context)
            .data(phone.uri)
            .placeholderMemoryCacheKey(thumbnailCacheKey(item))
            .build()
    }
}
