package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.MediaFile
import dev.ybdn.ciaocloud.domain.model.TransferAbortReason
import dev.ybdn.ciaocloud.domain.model.TransferProgress
import dev.ybdn.ciaocloud.domain.model.TransferRecord
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import dev.ybdn.ciaocloud.domain.repository.DestinationWriteResult
import dev.ybdn.ciaocloud.domain.repository.DestinationWriter
import dev.ybdn.ciaocloud.domain.repository.TransferStateRepository
import dev.ybdn.ciaocloud.domain.util.DestinationDecision
import dev.ybdn.ciaocloud.domain.util.DestinationPathResolver
import dev.ybdn.ciaocloud.domain.util.DuplicateResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * Copie un lot de fichiers vers le SSD, dossier par dossier calculé à partir de la date
 * effective, résout les collisions de nom, puis vérifie chaque copie avant de la marquer
 * "vérifiée" en base locale. N'écrit jamais deux fois sur un fichier existant à destination.
 * Un fichier déjà présent à l'identique dans le dossier du jour n'est pas recopié : il est
 * directement marqué vérifié.
 *
 * Un fichier illisible échoue seul ; en revanche le lot est interrompu si le SSD devient
 * inaccessible ou plein, pour ne pas enchaîner des échecs sur tous les fichiers restants.
 */
class TransferMediaUseCase(
    private val destinationWriter: DestinationWriter,
    private val transferStateRepository: TransferStateRepository,
    private val verifyTransferUseCase: VerifyTransferUseCase,
) {
    operator fun invoke(files: List<MediaFile>): Flow<TransferProgress> = channelFlow {
        var bytesTransferredSoFar = 0L
        var succeeded = 0
        var failed = 0
        var alreadyPresent = 0

        precheckAbortReason(files)?.let { reason ->
            send(TransferProgress.BatchCompleted(0, 0, 0, abortReason = reason))
            return@channelFlow
        }

        for ((index, file) in files.withIndex()) {
            val fileIndex = index + 1
            send(TransferProgress.FileStarted(file, fileIndex, files.size))
            transferStateRepository.upsert(
                TransferRecord(
                    mediaStoreId = file.mediaStoreId,
                    mediaType = file.mediaType,
                    status = TransferStatus.COPYING,
                ),
            )

            val outcome = try {
                copyAndVerify(file) { bytesProcessed ->
                    // Événement de progression non critique : perdu si le canal est plein.
                    trySend(TransferProgress.FileBytesCopied(file, bytesProcessed))
                }
            } catch (e: CancellationException) {
                transferStateRepository.markStatus(file.mediaStoreId, TransferStatus.FAILED, "Transfert interrompu")
                throw e
            } catch (e: Exception) {
                CopyOutcome.Failure(e.message ?: e::class.simpleName ?: "Erreur inconnue")
            }

            when (outcome) {
                is CopyOutcome.Success -> {
                    val writeResult = outcome.writeResult
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
                    send(
                        TransferProgress.FileVerified(
                            file = file,
                            destinationPath = writeResult.writtenRelativePath,
                            fileIndex = fileIndex,
                            totalFiles = files.size,
                            bytesTransferredSoFar = bytesTransferredSoFar,
                        ),
                    )
                }

                is CopyOutcome.AlreadyPresent -> {
                    transferStateRepository.upsert(
                        TransferRecord(
                            mediaStoreId = file.mediaStoreId,
                            mediaType = file.mediaType,
                            status = TransferStatus.VERIFIED,
                            destinationPath = outcome.destinationPath,
                            checksum = outcome.checksum,
                            sizeBytes = file.sizeBytes,
                        ),
                    )
                    alreadyPresent++
                    send(TransferProgress.FileAlreadyPresent(file, outcome.destinationPath, fileIndex, files.size))
                }

                is CopyOutcome.Failure -> {
                    transferStateRepository.markStatus(file.mediaStoreId, TransferStatus.FAILED, outcome.reason)
                    failed++
                    send(TransferProgress.FileFailed(file, fileIndex, files.size, outcome.reason))

                    abortReasonAfterFailure(file)?.let { reason ->
                        send(
                            TransferProgress.BatchCompleted(
                                succeeded,
                                failed,
                                bytesTransferredSoFar,
                                alreadyPresent,
                                reason,
                            ),
                        )
                        return@channelFlow
                    }
                }
            }
        }

        send(TransferProgress.BatchCompleted(succeeded, failed, bytesTransferredSoFar, alreadyPresent))
    }

    private suspend fun precheckAbortReason(files: List<MediaFile>): TransferAbortReason? {
        if (!destinationWriter.isDestinationAvailable()) return TransferAbortReason.DestinationUnavailable
        val required = files.sumOf { it.sizeBytes }
        val available = destinationWriter.availableBytes() ?: return null
        return if (available < required) TransferAbortReason.InsufficientSpace(required, available) else null
    }

    /** Distingue un échec propre au fichier d'un problème de destination qui ferait échouer tout le reste. */
    private suspend fun abortReasonAfterFailure(file: MediaFile): TransferAbortReason? {
        if (!destinationWriter.isDestinationAvailable()) return TransferAbortReason.DestinationUnavailable
        val available = destinationWriter.availableBytes() ?: return null
        return if (available < file.sizeBytes) TransferAbortReason.InsufficientSpace(file.sizeBytes, available) else null
    }

    private suspend fun copyAndVerify(file: MediaFile, onProgress: (Long) -> Unit): CopyOutcome {
        val relativeDirPath = DestinationPathResolver.resolveDestinationDirectory(
            epochMillis = file.effectiveDateEpochMillis,
            utcOffsetMinutes = file.captureUtcOffsetMinutes,
        )

        val decision = DuplicateResolver.resolve(
            desiredName = file.displayName,
            sourceSizeBytes = file.sizeBytes,
            existingEntries = destinationWriter.listExistingEntries(relativeDirPath),
        ) { existingName ->
            destinationWriter.identicalContentChecksum(file.uri, relativeDirPath, existingName, onProgress)
        }

        val availableName = when (decision) {
            is DestinationDecision.AlreadyPresent -> return CopyOutcome.AlreadyPresent(
                destinationPath = "$relativeDirPath/${decision.existingFileName}",
                checksum = decision.checksum,
            )
            is DestinationDecision.CopyAs -> decision.fileName
        }

        val writeResult = destinationWriter.writeFile(
            sourceUri = file.uri,
            relativeDirPath = relativeDirPath,
            fileName = availableName,
            onProgress = onProgress,
        )

        return when (
            val verifyResult = verifyTransferUseCase(
                sourceFile = file,
                writeResult = writeResult,
                relativeDirPath = relativeDirPath,
            )
        ) {
            is VerifyResult.Success -> CopyOutcome.Success(writeResult)
            is VerifyResult.Failure -> CopyOutcome.Failure(verifyResult.reason)
        }
    }

    private sealed interface CopyOutcome {
        data class Success(val writeResult: DestinationWriteResult) : CopyOutcome
        data class AlreadyPresent(val destinationPath: String, val checksum: String) : CopyOutcome
        data class Failure(val reason: String) : CopyOutcome
    }
}
