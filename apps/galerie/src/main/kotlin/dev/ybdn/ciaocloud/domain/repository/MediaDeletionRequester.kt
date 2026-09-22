package dev.ybdn.ciaocloud.domain.repository

import dev.ybdn.ciaocloud.domain.model.TransferRecord

/**
 * Déclenche la suppression groupée des originaux via `MediaStore.createDeleteRequest()`
 * (API 30+), avec confirmation système, plutôt qu'une gestion manuelle de
 * `RecoverableSecurityException`. L'implémentation vit côté presentation car elle nécessite
 * un `ActivityResultLauncher`.
 */
interface MediaDeletionRequester {
    /** @return true si l'utilisateur a confirmé et que les fichiers ont été supprimés. */
    suspend fun requestDelete(records: List<TransferRecord>): Boolean
}
