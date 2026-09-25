package dev.ybdn.ciao.galerie.domain.usecase

import dev.ybdn.ciao.galerie.domain.model.FavoriteKeys
import dev.ybdn.ciao.galerie.domain.model.GalleryItem
import dev.ybdn.ciao.galerie.domain.model.TransferStatus
import dev.ybdn.ciao.galerie.domain.repository.FavoritesRepository
import dev.ybdn.ciao.galerie.domain.repository.MediaTrash
import dev.ybdn.ciao.galerie.domain.repository.SsdMediaBrowser
import dev.ybdn.ciao.galerie.domain.repository.SsdMediaIndex
import dev.ybdn.ciao.galerie.domain.repository.SsdThumbnailCache
import dev.ybdn.ciao.galerie.domain.repository.TransactionRunner
import dev.ybdn.ciao.galerie.domain.repository.TransferStateRepository
import dev.ybdn.ciao.galerie.domain.repository.TrashedMedia
import dev.ybdn.ciao.galerie.domain.repository.TriageRepository
import dev.ybdn.ciao.galerie.domain.repository.TrashedMediaSource
import kotlinx.coroutines.flow.Flow

/** Où supprimer les éléments sélectionnés. */
enum class DeleteTarget { PHONE, SSD, EVERYWHERE }

sealed interface DeleteItemsOutcome {
    data class Done(
        val phoneTrashed: Int,
        val ssdDeleted: Int,
        val ssdFailed: Int,
        /** Clés (`GalleryItem.key`) des éléments supprimés de tous les emplacements visés. */
        val completedKeys: Set<String> = emptySet(),
    ) : DeleteItemsOutcome
    /** Confirmation système refusée : rien n'a été supprimé. */
    data object Cancelled : DeleteItemsOutcome
    data object SsdUnavailable : DeleteItemsOutcome
}

/**
 * Suppression depuis la galerie. Téléphone : corbeille système (réversible 30 jours), l'état de
 * transfert passe en `DELETED`. SSD : suppression définitive (confirmée dans l'app au préalable),
 * qui invalide l'état de transfert du média encore sur le téléphone : il redevient proposé au
 * transfert et ne peut plus être libéré comme « vérifié ». Favori et décision de tri suivent le
 * média resté sur le téléphone, ou disparaissent avec lui.
 */
class DeleteGalleryItemsUseCase(
    private val mediaTrash: MediaTrash,
    private val ssdMediaBrowser: SsdMediaBrowser,
    private val ssdMediaIndex: SsdMediaIndex,
    private val ssdThumbnailCache: SsdThumbnailCache,
    private val transferStateRepository: TransferStateRepository,
    private val favoritesRepository: FavoritesRepository,
    private val triageRepository: TriageRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(items: List<GalleryItem>, target: DeleteTarget): DeleteItemsOutcome {
        val deletePhone = target != DeleteTarget.SSD
        val deleteSsd = target != DeleteTarget.PHONE
        val phoneItems = if (deletePhone) items.filter { it.phone != null } else emptyList()
        val ssdItems = if (deleteSsd) items.filter { it.ssd != null } else emptyList()

        if (ssdItems.isNotEmpty() && !ssdMediaBrowser.refreshAvailability()) return DeleteItemsOutcome.SsdUnavailable

        var phoneTrashed = 0
        for (batch in phoneItems.chunked(MAX_URIS_PER_REQUEST)) {
            if (!mediaTrash.moveToTrash(batch.map { it.phone!!.uri })) {
                if (phoneTrashed == 0) return DeleteItemsOutcome.Cancelled
                break
            }
            val ids = batch.map { it.phone!!.mediaStoreId }
            transferStateRepository.getByMediaStoreIds(ids)
                .filter { it.status == TransferStatus.VERIFIED }
                .forEach { transferStateRepository.markStatus(it.mediaStoreId, TransferStatus.DELETED) }
            phoneTrashed += batch.size
        }
        // Téléphone refusé en partie : on ne touche pas au SSD pour les éléments encore sur le téléphone.
        val trashedKeys = phoneItems.take(phoneTrashed).mapTo(HashSet()) { it.key }
        val ssdToDelete = if (target == DeleteTarget.EVERYWHERE) {
            ssdItems.filter { it.phone == null || it.key in trashedKeys }
        } else {
            ssdItems
        }

        var ssdDeleted = 0
        var ssdFailed = 0
        val ssdDeletedKeys = HashSet<String>()
        for (item in ssdToDelete) {
            val ssd = item.ssd!!
            if (!ssdMediaBrowser.delete(ssd.relativePath)) {
                ssdFailed++
                continue
            }
            ssdDeletedKeys += item.key
            val phoneStillPresent = item.phone != null && item.key !in trashedKeys
            transactionRunner.inTransaction {
                ssdMediaIndex.remove(ssd.relativePath)
                transferStateRepository.getByDestinationPath(ssd.relativePath)
                    .forEach { transferStateRepository.delete(it.mediaStoreId) }
                val ssdKey = FavoriteKeys.ssd(ssd.relativePath)
                if (phoneStillPresent) {
                    val phoneKey = FavoriteKeys.phone(item.phone!!.mediaStoreId)
                    favoritesRepository.renameKey(ssdKey, phoneKey)
                    triageRepository.renameKey(ssdKey, phoneKey)
                } else {
                    favoritesRepository.setFavorite(listOf(ssdKey), favorite = false)
                    triageRepository.delete(listOf(ssdKey))
                }
            }
            ssdThumbnailCache.remove(ssd.relativePath)
            ssdDeleted++
        }
        val completedKeys = items.filter { item ->
            val phoneDone = !deletePhone || item.phone == null || item.key in trashedKeys
            val ssdDone = !deleteSsd || item.ssd == null || item.key in ssdDeletedKeys
            phoneDone && ssdDone
        }.mapTo(HashSet()) { it.key }
        return DeleteItemsOutcome.Done(phoneTrashed, ssdDeleted, ssdFailed, completedKeys)
    }

    private companion object {
        const val MAX_URIS_PER_REQUEST = 500
    }
}

/**
 * Restauration depuis la corbeille système. Un média sauvegardé dont la copie est toujours indexée
 * sur le SSD redevient « vérifié ».
 */
class RestoreFromTrashUseCase(
    private val mediaTrash: MediaTrash,
    private val transferStateRepository: TransferStateRepository,
    private val ssdMediaIndex: SsdMediaIndex,
) {
    suspend operator fun invoke(mediaStoreIdsToUris: Map<Long, String>): Boolean {
        if (mediaStoreIdsToUris.isEmpty()) return true
        if (!mediaTrash.restore(mediaStoreIdsToUris.values.toList())) return false
        transferStateRepository.getByMediaStoreIds(mediaStoreIdsToUris.keys)
            .filter { it.status == TransferStatus.DELETED && it.destinationPath != null }
            .filter { ssdMediaIndex.get(it.destinationPath!!) != null }
            .forEach { transferStateRepository.markStatus(it.mediaStoreId, TransferStatus.VERIFIED) }
        return true
    }
}

/** Suppression définitive depuis la corbeille système. */
class DeleteFromTrashUseCase(
    private val mediaTrash: MediaTrash,
) {
    suspend operator fun invoke(uris: List<String>): Boolean = uris.isEmpty() || mediaTrash.deletePermanently(uris)
}

class ToggleFavoriteUseCase(
    private val favoritesRepository: FavoritesRepository,
) {
    /** Ajoute tous les éléments aux favoris, sauf s'ils le sont déjà tous : ils en sont alors retirés. */
    suspend operator fun invoke(items: List<GalleryItem>) {
        val favorite = !items.all { it.isFavorite }
        favoritesRepository.setFavorite(items.map { it.favoriteKey }, favorite)
    }
}

class ObserveTrashUseCase(
    private val trashedMediaSource: TrashedMediaSource,
) {
    operator fun invoke(): Flow<List<TrashedMedia>> = trashedMediaSource.observeTrashed()
}
