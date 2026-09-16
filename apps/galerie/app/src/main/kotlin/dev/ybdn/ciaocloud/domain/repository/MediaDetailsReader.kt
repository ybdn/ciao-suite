package dev.ybdn.ciaocloud.domain.repository

import dev.ybdn.ciaocloud.domain.model.MediaDetails
import dev.ybdn.ciaocloud.domain.model.MediaType

/** Lecture des métadonnées EXIF (photo) ou conteneur (vidéo) d'un original. */
interface MediaDetailsReader {
    /** @param isMediaStoreUri true pour une URI MediaStore (accès à l'original avec GPS). */
    suspend fun read(uri: String, mediaType: MediaType, isMediaStoreUri: Boolean): MediaDetails?
}
