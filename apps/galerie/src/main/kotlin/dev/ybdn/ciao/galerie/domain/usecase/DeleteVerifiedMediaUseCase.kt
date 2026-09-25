package dev.ybdn.ciao.galerie.domain.usecase

import dev.ybdn.ciao.galerie.domain.model.TransferStatus
import dev.ybdn.ciao.galerie.domain.repository.MediaDeletionRequester
import dev.ybdn.ciao.galerie.domain.repository.MediaRepository
import dev.ybdn.ciao.galerie.domain.repository.TransferStateRepository

/**
 * Ne supprime que les originaux dont la copie est vérifiée, via `MediaStore.createDeleteRequest()`
 * (confirmation système), puis marque les enregistrements correspondants comme supprimés.
 * Les originaux déjà disparus du téléphone (supprimés ailleurs) sont simplement marqués.
 */
class DeleteVerifiedMediaUseCase(
    private val transferStateRepository: TransferStateRepository,
    private val mediaRepository: MediaRepository,
    private val mediaDeletionRequester: MediaDeletionRequester,
) {
    suspend operator fun invoke(): DeleteOutcome {
        val verifiedRecords = transferStateRepository.getByStatus(TransferStatus.VERIFIED)
        if (verifiedRecords.isEmpty()) return DeleteOutcome(requested = 0, deleted = 0)

        val existingIds = mediaRepository.findExistingIds(verifiedRecords.map { it.mediaStoreId })
        val (toDelete, alreadyGone) = verifiedRecords.partition { it.mediaStoreId in existingIds }
        alreadyGone.forEach { transferStateRepository.markStatus(it.mediaStoreId, TransferStatus.DELETED) }

        var deleted = 0
        // Une confirmation système par lot : évite de dépasser la taille max d'une transaction Binder.
        for (batch in toDelete.chunked(MAX_URIS_PER_REQUEST)) {
            if (!mediaDeletionRequester.requestDelete(batch)) break
            batch.forEach { transferStateRepository.markStatus(it.mediaStoreId, TransferStatus.DELETED) }
            deleted += batch.size
        }

        return DeleteOutcome(requested = toDelete.size, deleted = deleted)
    }

    private companion object {
        const val MAX_URIS_PER_REQUEST = 1000
    }
}

data class DeleteOutcome(
    val requested: Int,
    val deleted: Int,
) {
    val isComplete: Boolean get() = deleted == requested
}
