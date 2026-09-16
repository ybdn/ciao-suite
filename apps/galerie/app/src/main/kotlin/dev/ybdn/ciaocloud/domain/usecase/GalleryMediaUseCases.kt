package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.repository.SsdMediaBrowser
import dev.ybdn.ciaocloud.domain.repository.SsdThumbnailCache
import kotlinx.coroutines.flow.StateFlow

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
