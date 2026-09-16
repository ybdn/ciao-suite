package dev.ybdn.ciaocloud.domain.repository

import dev.ybdn.ciaocloud.domain.model.MediaFile

/** Accès en lecture aux photos/vidéos locales du téléphone (implémenté via MediaStore). */
interface MediaRepository {
    suspend fun scanLocalMedia(): List<MediaFile>
}
