package dev.ybdn.ciao.galerie.domain.usecase

import dev.ybdn.ciao.galerie.domain.model.MediaFile
import dev.ybdn.ciao.galerie.domain.model.ScanSession
import dev.ybdn.ciao.galerie.domain.model.TransferStatus
import dev.ybdn.ciao.galerie.domain.repository.DestinationWriter
import dev.ybdn.ciao.galerie.domain.repository.MediaRepository
import dev.ybdn.ciao.galerie.domain.repository.TransferStateRepository
import dev.ybdn.ciao.galerie.domain.util.CaptureOffsetInferrer

/**
 * Scanne les médias locaux et exclut ceux déjà transférés + vérifiés lors d'une session
 * précédente, pour ne jamais re-proposer un fichier déjà en sécurité sur le SSD. Le résultat
 * est publié dans [ScanSession] pour être transféré tel quel.
 */
class ScanLocalMediaUseCase(
    private val mediaRepository: MediaRepository,
    private val transferStateRepository: TransferStateRepository,
    private val scanSession: ScanSession,
    private val destinationWriter: DestinationWriter,
) {
    suspend operator fun invoke(): List<MediaFile> {
        // Inférence sur tout le scan (y compris les médias déjà vérifiés) : plus de photos de référence.
        val localMedia = CaptureOffsetInferrer.inferVideoOffsets(
            mediaRepository.scanLocalMedia(
                excludedRelativePath = destinationWriter.phoneStorageRelativePath(),
            ),
        )
        val verifiedIds = transferStateRepository.getByStatus(TransferStatus.VERIFIED)
            .map { it.mediaStoreId }
            .toSet()

        return localMedia.filterNot { it.mediaStoreId in verifiedIds }
            .also(scanSession::update)
    }
}
