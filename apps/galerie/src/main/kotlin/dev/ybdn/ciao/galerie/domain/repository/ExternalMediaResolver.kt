package dev.ybdn.ciao.galerie.domain.repository

import dev.ybdn.ciao.galerie.domain.model.PhoneMedia

/**
 * Métadonnées d'URI reçues d'autres apps (ouverture d'une pièce jointe, retour de l'appareil
 * photo). `mediaStoreId` est négatif si l'URI n'appartient pas à MediaStore : elle n'est alors ni
 * supprimable ni favorisable.
 */
interface ExternalMediaResolver {
    suspend fun resolve(uris: List<String>, mimeTypeHint: String?): List<PhoneMedia>

    /** Identifiant MediaStore du volume interne pour [uri], ou null si l'URI n'en est pas une. */
    fun mediaStoreIdOf(uri: String): Long?
}
