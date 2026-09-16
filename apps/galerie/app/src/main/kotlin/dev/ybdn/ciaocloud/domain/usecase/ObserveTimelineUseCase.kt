package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.repository.DestinationWriter
import dev.ybdn.ciaocloud.domain.repository.FavoritesRepository
import dev.ybdn.ciaocloud.domain.repository.PhoneGallerySource
import dev.ybdn.ciaocloud.domain.repository.SsdMediaIndex
import dev.ybdn.ciaocloud.domain.repository.TransferStateRepository
import dev.ybdn.ciaocloud.domain.util.MediaFileTypes
import dev.ybdn.ciaocloud.domain.util.TimelineBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn

/**
 * Chronologie unifiée téléphone + SSD, recalculée à chaque changement d'une des sources. Le flux
 * est partagé : la grille et la visionneuse observent la même liste sans relire MediaStore.
 */
class ObserveTimelineUseCase(
    private val phoneGallerySource: PhoneGallerySource,
    ssdMediaIndex: SsdMediaIndex,
    transferStateRepository: TransferStateRepository,
    favoritesRepository: FavoritesRepository,
    destinationWriter: DestinationWriter,
    scope: CoroutineScope,
) {
    private val timeline: Flow<List<GalleryItem>> = combine(
        phoneGallerySource.observeMedia(),
        ssdMediaIndex.observeAll(),
        transferStateRepository.observeAll(),
        favoritesRepository.observeKeys(),
    ) { phone, ssd, records, favorites ->
        // Destination de test dans le stockage interne : ses copies sont des médias SSD, pas téléphone.
        val excludedPath = destinationWriter.phoneStorageRelativePath()
        val phoneOnly = if (excludedPath == null) {
            phone
        } else {
            phone.filterNot { it.relativePath?.startsWith(excludedPath, ignoreCase = true) == true }
        }
        // Index construit avant le filtrage des `._*` : ignorés sans attendre une réindexation.
        val ssdMedia = ssd.filterNot { MediaFileTypes.isAppleDouble(it.displayName) }
        TimelineBuilder.build(phoneOnly, ssdMedia, records, favorites)
    }
        .conflate()
        .flowOn(Dispatchers.Default)
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    operator fun invoke(): Flow<List<GalleryItem>> = timeline

    /** Relit les médias du téléphone, ex. après l'octroi de la permission d'accès. */
    fun refresh() = phoneGallerySource.refresh()

    private companion object {
        const val STOP_TIMEOUT_MS = 10_000L
    }
}
