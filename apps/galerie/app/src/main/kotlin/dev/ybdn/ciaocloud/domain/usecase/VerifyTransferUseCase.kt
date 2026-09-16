package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.MediaFile
import dev.ybdn.ciaocloud.domain.repository.DestinationWriteResult
import dev.ybdn.ciaocloud.domain.repository.DestinationWriter

sealed interface VerifyResult {
    data class Success(val checksum: String) : VerifyResult
    data class Failure(val reason: String) : VerifyResult
}

/**
 * Vérifie une copie déjà écrite sur le SSD en la relisant : sa taille doit correspondre à la
 * source et son checksum à celui calculé pendant l'écriture, avant de marquer le transfert
 * comme vérifié.
 */
class VerifyTransferUseCase(
    private val destinationWriter: DestinationWriter,
) {
    suspend operator fun invoke(
        sourceFile: MediaFile,
        writeResult: DestinationWriteResult,
        relativeDirPath: String,
    ): VerifyResult {
        val readBack = destinationWriter.readBackForVerification(relativeDirPath, writeResult.writtenFileName)
            ?: return VerifyResult.Failure("Fichier copié introuvable pour vérification")

        if (writeResult.bytesWritten != sourceFile.sizeBytes || readBack.bytesWritten != sourceFile.sizeBytes) {
            return VerifyResult.Failure(
                "Taille différente : source=${sourceFile.sizeBytes}, écrite=${writeResult.bytesWritten}, " +
                    "relue=${readBack.bytesWritten}",
            )
        }
        if (readBack.checksum != writeResult.checksum) {
            return VerifyResult.Failure("Checksum différent après copie")
        }
        return VerifyResult.Success(readBack.checksum)
    }
}
