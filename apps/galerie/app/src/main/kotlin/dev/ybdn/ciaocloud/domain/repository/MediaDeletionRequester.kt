package dev.ybdn.ciaocloud.domain.repository

/**
 * Déclenche la suppression groupée des originaux via `MediaStore.createDeleteRequest()`
 * (API 30+), avec confirmation système, plutôt qu'une gestion manuelle de
 * `RecoverableSecurityException`. L'implémentation vit côté presentation car elle nécessite
 * un `ActivityResultLauncher`.
 */
interface MediaDeletionRequester {
    suspend fun requestDelete(mediaStoreIds: List<Long>): Boolean
}
