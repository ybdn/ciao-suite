package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.FavoriteKeys
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.repository.ExternalMediaResolver
import dev.ybdn.ciaocloud.domain.model.MediaDetails
import dev.ybdn.ciaocloud.domain.repository.MediaDetailsReader
import dev.ybdn.ciaocloud.domain.repository.SsdMediaBrowser
import dev.ybdn.ciaocloud.domain.repository.SsdThumbnailCache
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneId

/** URI de l'original à afficher : téléphone en priorité, sinon SSD s'il est branché (null sinon). */
class GetOriginalUriUseCase(
    private val ssdMediaBrowser: SsdMediaBrowser,
) {
    suspend operator fun invoke(item: GalleryItem): String? =
        item.phone?.uri ?: item.ssd?.let { ssdMediaBrowser.documentUri(it.relativePath) }
}

/** Disponibilité du SSD pour la galerie (estompage des médias SSD seuls, affichage des originaux). */
class ObserveSsdAvailabilityUseCase(
    private val ssdMediaBrowser: SsdMediaBrowser,
) {
    operator fun invoke(): StateFlow<Boolean> = ssdMediaBrowser.isAvailable

    suspend fun refresh(): Boolean = ssdMediaBrowser.refreshAvailability()
}

/** Gestion du cache local des vignettes SSD (réglages). */
class ManageThumbnailCacheUseCase(
    private val ssdThumbnailCache: SsdThumbnailCache,
) {
    suspend fun sizeBytes(): Long = ssdThumbnailCache.sizeBytes()

    suspend fun clear() = ssdThumbnailCache.clear()
}

/** Métadonnées détaillées de l'original (téléphone en priorité, sinon SSD branché). */
class GetMediaDetailsUseCase(
    private val mediaDetailsReader: MediaDetailsReader,
    private val ssdMediaBrowser: SsdMediaBrowser,
) {
    suspend operator fun invoke(item: GalleryItem): MediaDetails? {
        item.phone?.let { return mediaDetailsReader.read(it.uri, it.mediaType, isMediaStoreUri = true) }
        val ssd = item.ssd ?: return null
        val uri = ssdMediaBrowser.documentUri(ssd.relativePath) ?: return null
        return mediaDetailsReader.read(uri, ssd.mediaType, isMediaStoreUri = false)
    }
}

/** Éléments de visionneuse pour des URI reçues d'autres apps (hors chronologie). */
class ResolveExternalMediaUseCase(
    private val externalMediaResolver: ExternalMediaResolver,
) {
    suspend operator fun invoke(uris: List<String>, mimeTypeHint: String?): List<GalleryItem> =
        externalMediaResolver.resolve(uris, mimeTypeHint).mapIndexed { index, media ->
            GalleryItem(
                key = "external:$index",
                phone = media,
                ssd = null,
                captureDate = Instant.ofEpochMilli(media.takenAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate(),
                sortEpochMillis = media.takenAtEpochMillis,
            )
        }

    /** Clé de chronologie d'une URI MediaStore du volume interne (`phone:<id>`), sinon null. */
    fun timelineKeyOf(uri: String): String? = externalMediaResolver.mediaStoreIdOf(uri)?.let(FavoriteKeys::phone)
}
