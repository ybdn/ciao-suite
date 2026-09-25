package dev.ybdn.ciao.galerie.domain.model

/** Événement de progression émis pendant le transfert d'un lot de fichiers. */
sealed interface TransferProgress {
    data class FileStarted(
        val file: MediaFile,
        val fileIndex: Int,
        val totalFiles: Int,
    ) : TransferProgress

    /** Progression de la copie du fichier en cours (utile pour les vidéos volumineuses). */
    data class FileBytesCopied(
        val file: MediaFile,
        val bytesCopied: Long,
    ) : TransferProgress

    data class FileVerified(
        val file: MediaFile,
        val destinationPath: String,
        val fileIndex: Int,
        val totalFiles: Int,
        val bytesTransferredSoFar: Long,
    ) : TransferProgress

    /** Un fichier identique existait déjà sur le SSD : aucune copie, le média est tout de même vérifié. */
    data class FileAlreadyPresent(
        val file: MediaFile,
        val destinationPath: String,
        val fileIndex: Int,
        val totalFiles: Int,
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
        /** Fichiers déjà présents à l'identique sur le SSD, non recopiés (distincts de [succeeded]). */
        val alreadyPresent: Int = 0,
        /** Non null si le lot a été interrompu avant la fin. */
        val abortReason: TransferAbortReason? = null,
    ) : TransferProgress
}

/** Raison d'une interruption du lot entier (par opposition à l'échec d'un seul fichier). */
sealed interface TransferAbortReason {
    /** SSD débranché, permission SAF révoquée ou dossier racine introuvable. */
    data object DestinationUnavailable : TransferAbortReason

    data class InsufficientSpace(
        val requiredBytes: Long,
        val availableBytes: Long,
    ) : TransferAbortReason
}
