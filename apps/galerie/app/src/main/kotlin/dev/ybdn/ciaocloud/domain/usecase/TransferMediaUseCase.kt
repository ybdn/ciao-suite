package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.MediaFile
import dev.ybdn.ciaocloud.domain.model.TransferProgress
import dev.ybdn.ciaocloud.domain.model.TransferRecord
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import dev.ybdn.ciaocloud.domain.repository.DestinationWriter
import dev.ybdn.ciaocloud.domain.repository.TransferStateRepository
import dev.ybdn.ciaocloud.domain.util.DestinationPathResolver
import dev.ybdn.ciaocloud.domain.util.FileNameCollisionResolver
import kotlinx.coroutines.flow.flow

/**
 * Copie un lot de fichiers vers le SSD, dossier par dossier calculé à partir de la date
 * effective, résout les collisions de nom, puis vérifie chaque copie avant de la marquer
 * "vérifiée" en base locale. N'écrit jamais deux fois sur un fichier existant à destination.
 */
class TransferMediaUseCase(
    private val destinationWriter: DestinationWriter,
    private val transferStateRepository: TransferStateRepository,
    private val verifyTransferUseCase: VerifyTransferUseCase,
) {
    operator fun invoke(files: List<MediaFile>) = flow {
        var bytesTransferredSoFar = 0L
        var succeeded = 0
        var failed = 0

        files.forEachIndexed { index, file ->
            val fileIndex = index + 1
            emit(TransferProgress.FileStarted(file, fileIndex, files.size))
            transferStateRepository.upsert(
                TransferRecord(
                    mediaStoreId = file.mediaStoreId,
                    mediaType = file.mediaType,
                    status = TransferStatus.COPYING,
                ),
            )

            val relativeDirPath = DestinationPathResolver.resolveDestinationDirectory(
                file.effectiveDateEpochMillis,
            )

            val outcome = runCatching {
                val existingNames = destinationWriter.listExistingFileNames(relativeDirPath)
                val availableName = FileNameCollisionResolver.resolveAvailableName(
                    desiredName = file.displayName,
                ) { candidate -> candidate in existingNames }

                val writeResult = destinationWriter.writeFile(
                    sourceUri = file.uri,
                    sourceSizeBytes = file.sizeBytes,
                    relativeDirPath = relativeDirPath,
                    fileName = availableName,
                )

                val verifyResult = verifyTransferUseCase(
                    sourceFile = file,
                    expectedChecksum = writeResult.checksum,
                    relativeDirPath = relativeDirPath,
                    fileName = availableName,
                )

                writeResult to verifyResult
            }

            val result = outcome.getOrNull()
            val (writeResult, verifyResult) = result ?: (null to null)

            if (outcome.isFailure || verifyResult is VerifyResult.Failure || writeResult == null) {
                val reason = outcome.exceptionOrNull()?.message
                    ?: (verifyResult as? VerifyResult.Failure)?.reason
                    ?: "Échec de copie inconnu"
                transferStateRepository.markStatus(file.mediaStoreId, TransferStatus.FAILED, reason)
                failed++
                emit(TransferProgress.FileFailed(file, fileIndex, files.size, reason))
            } else {
                transferStateRepository.upsert(
                    TransferRecord(
                        mediaStoreId = file.mediaStoreId,
                        mediaType = file.mediaType,
                        status = TransferStatus.VERIFIED,
                        destinationPath = writeResult.writtenRelativePath,
                        checksum = writeResult.checksum,
                        sizeBytes = writeResult.bytesWritten,
                    ),
                )
                bytesTransferredSoFar += writeResult.bytesWritten
                succeeded++
                emit(
                    TransferProgress.FileVerified(
                        file = file,
                        destinationPath = writeResult.writtenRelativePath,
                        fileIndex = fileIndex,
                        totalFiles = files.size,
                        bytesTransferredSoFar = bytesTransferredSoFar,
                    ),
                )
            }
        }

        emit(TransferProgress.BatchCompleted(succeeded, failed, bytesTransferredSoFar))
    }
}
