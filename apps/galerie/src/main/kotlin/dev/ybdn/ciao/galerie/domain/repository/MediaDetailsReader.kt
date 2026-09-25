package dev.ybdn.ciao.galerie.domain.repository

import dev.ybdn.ciao.galerie.domain.model.MediaDetails
import dev.ybdn.ciao.galerie.domain.model.MediaType

/** Lecture des métadonnées EXIF (photo) ou conteneur (vidéo) d'un original. */
interface MediaDetailsReader {
    /** @param isMediaStoreUri true pour une URI MediaStore (accès à l'original avec GPS). */
    suspend fun read(uri: String, mediaType: MediaType, isMediaStoreUri: Boolean): MediaDetails?
}
