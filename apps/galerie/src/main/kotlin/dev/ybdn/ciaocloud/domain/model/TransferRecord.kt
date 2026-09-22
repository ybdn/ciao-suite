package dev.ybdn.ciaocloud.domain.model

/** État de transfert persisté pour un média, indépendamment de la session en cours. */
data class TransferRecord(
    val mediaStoreId: Long,
    val mediaType: MediaType,
    val status: TransferStatus,
    val destinationPath: String? = null,
    val checksum: String? = null,
    val errorMessage: String? = null,
    val sizeBytes: Long = 0,
)
