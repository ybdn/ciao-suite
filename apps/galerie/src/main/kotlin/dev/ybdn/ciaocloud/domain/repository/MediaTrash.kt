package dev.ybdn.ciaocloud.domain.repository

import dev.ybdn.ciaocloud.domain.model.PhoneMedia
import kotlinx.coroutines.flow.Flow

/**
 * Corbeille système du téléphone (MediaStore, API 30+). Chaque opération passe par une
 * confirmation système ; elles renvoient true si l'utilisateur a accepté.
 */
interface MediaTrash {
    suspend fun moveToTrash(uris: List<String>): Boolean

    suspend fun restore(uris: List<String>): Boolean

    suspend fun deletePermanently(uris: List<String>): Boolean
}

/** Médias du téléphone actuellement dans la corbeille système. */
interface TrashedMediaSource {
    fun observeTrashed(): Flow<List<TrashedMedia>>
}

data class TrashedMedia(
    val media: PhoneMedia,
    /** Date de suppression définitive automatique par le système. */
    val expiresAtEpochMillis: Long,
)
