package dev.ybdn.ciaocloud.domain.repository

import dev.ybdn.ciaocloud.domain.model.MediaFile

/** Accès en lecture aux photos/vidéos locales du téléphone (implémenté via MediaStore). */
interface MediaRepository {
    /** @param excludedRelativePath dossier (et sous-dossiers) à ignorer, au format MediaStore "Documents/Test/". */
    suspend fun scanLocalMedia(excludedRelativePath: String? = null): List<MediaFile>

    /** Parmi [mediaStoreIds], renvoie ceux qui existent encore sur le téléphone. */
    suspend fun findExistingIds(mediaStoreIds: Collection<Long>): Set<Long>
}
