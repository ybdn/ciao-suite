package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.MediaFile
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import dev.ybdn.ciaocloud.domain.repository.MediaRepository
import dev.ybdn.ciaocloud.domain.repository.TransferStateRepository

/**
 * Scanne les médias locaux et exclut ceux déjà transférés + vérifiés lors d'une session
 * précédente, pour ne jamais re-proposer un fichier déjà en sécurité sur le SSD.
 */
class ScanLocalMediaUseCase(
    private val mediaRepository: MediaRepository,
    private val transferStateRepository: TransferStateRepository,
) {
    suspend operator fun invoke(): List<MediaFile> {
        val localMedia = mediaRepository.scanLocalMedia()
        val verifiedIds = transferStateRepository.getByStatus(TransferStatus.VERIFIED)
            .map { it.mediaStoreId }
            .toSet()

        return localMedia.filterNot { it.mediaStoreId in verifiedIds }
    }
}
