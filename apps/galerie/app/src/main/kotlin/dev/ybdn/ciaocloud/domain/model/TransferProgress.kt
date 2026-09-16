package dev.ybdn.ciaocloud.domain.model

/** Événement de progression émis pendant le transfert d'un lot de fichiers. */
sealed interface TransferProgress {
    data class FileStarted(
        val file: MediaFile,
        val fileIndex: Int,
        val totalFiles: Int,
    ) : TransferProgress

    data class FileVerified(
        val file: MediaFile,
        val destinationPath: String,
        val fileIndex: Int,
        val totalFiles: Int,
        val bytesTransferredSoFar: Long,
    ) : TransferProgress

    data class FileFailed(
        val file: MediaFile,
        val fileIndex: Int,
        val totalFiles: Int,
        val reason: String,
    ) : TransferProgress

    data class BatchCompleted(
        val succeeded: Int,
        val failed: Int,
        val totalBytesTransferred: Long,
    ) : TransferProgress
}
