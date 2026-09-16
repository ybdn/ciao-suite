package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.repository.DestinationWriter

data class DestinationStatus(
    val isAvailable: Boolean,
    val name: String? = null,
    val availableBytes: Long? = null,
)

/** État courant du SSD (branché/accessible, nom, espace libre) pour le résumé pré-transfert. */
class GetDestinationStatusUseCase(
    private val destinationWriter: DestinationWriter,
) {
    suspend operator fun invoke(): DestinationStatus {
        if (!destinationWriter.isDestinationAvailable()) return DestinationStatus(isAvailable = false)
        return DestinationStatus(
            isAvailable = true,
            name = destinationWriter.destinationName(),
            availableBytes = destinationWriter.availableBytes(),
        )
    }
}
