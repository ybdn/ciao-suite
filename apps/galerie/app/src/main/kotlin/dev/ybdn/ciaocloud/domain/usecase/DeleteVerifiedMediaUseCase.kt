package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.TransferRecord
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import dev.ybdn.ciaocloud.domain.repository.MediaDeletionRequester
import dev.ybdn.ciaocloud.domain.repository.TransferStateRepository

/**
 * Ne supprime que les originaux dont la copie est vérifiée, via `MediaStore.createDeleteRequest()`
 * (confirmation système), puis marque les enregistrements correspondants comme supprimés.
 */
class DeleteVerifiedMediaUseCase(
    private val transferStateRepository: TransferStateRepository,
    private val mediaDeletionRequester: MediaDeletionRequester,
) {
    suspend operator fun invoke(): DeleteOutcome {
        val verifiedRecords: List<TransferRecord> =
            transferStateRepository.getByStatus(TransferStatus.VERIFIED)

        if (verifiedRecords.isEmpty()) {
            return DeleteOutcome(requested = 0, granted = false)
        }

        val mediaStoreIds = verifiedRecords.map { it.mediaStoreId }
        val granted = mediaDeletionRequester.requestDelete(mediaStoreIds)

        if (granted) {
            mediaStoreIds.forEach { id ->
                transferStateRepository.markStatus(id, TransferStatus.DELETED)
            }
        }

        return DeleteOutcome(requested = mediaStoreIds.size, granted = granted)
    }
}

data class DeleteOutcome(
    val requested: Int,
    val granted: Boolean,
)
