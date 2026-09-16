package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.MediaFile
import dev.ybdn.ciaocloud.domain.repository.DestinationWriter

sealed interface VerifyResult {
    data class Success(val checksum: String) : VerifyResult
    data class Failure(val reason: String) : VerifyResult
}

/**
 * Vérifie une copie déjà écrite sur le SSD en comparant taille + checksum avec le fichier
 * source, avant de marquer le transfert comme vérifié.
 */
class VerifyTransferUseCase(
    private val destinationWriter: DestinationWriter,
) {
    suspend operator fun invoke(
        sourceFile: MediaFile,
        expectedChecksum: String,
        relativeDirPath: String,
        fileName: String,
    ): VerifyResult {
        val readBack = destinationWriter.readBackForVerification(relativeDirPath, fileName)
            ?: return VerifyResult.Failure("Fichier copié introuvable pour vérification")

        if (readBack.bytesWritten != sourceFile.sizeBytes) {
            return VerifyResult.Failure(
                "Taille différente : source=${sourceFile.sizeBytes}, copie=${readBack.bytesWritten}",
            )
        }
        if (readBack.checksum != expectedChecksum) {
            return VerifyResult.Failure("Checksum différent après copie")
        }
        return VerifyResult.Success(readBack.checksum)
    }
}
