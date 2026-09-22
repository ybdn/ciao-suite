package dev.ybdn.ciaocloud.domain.repository

import dev.ybdn.ciaocloud.domain.model.PhoneMedia
import kotlinx.coroutines.flow.Flow

/** Médias du téléphone pour la galerie (volume interne), actualisés à chaque changement de MediaStore. */
interface PhoneGallerySource {
    fun observeMedia(): Flow<List<PhoneMedia>>

    /** Force une relecture, ex. après l'octroi de la permission d'accès aux médias. */
    fun refresh()
}
