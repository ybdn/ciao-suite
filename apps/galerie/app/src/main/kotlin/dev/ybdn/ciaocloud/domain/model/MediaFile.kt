package dev.ybdn.ciaocloud.domain.model

/**
 * Représentation métier d'une photo ou vidéo locale candidate au transfert.
 *
 * [uri] est conservée en String pour que le domaine reste indépendant d'Android
 * (`android.net.Uri`) : la conversion se fait dans la couche data.
 */
data class MediaFile(
    val mediaStoreId: Long,
    val uri: String,
    val displayName: String,
    val mediaType: MediaType,
    val mimeType: String,
    val sizeBytes: Long,
    /** Date de prise de vue EXIF (photo) ou métadonnées vidéo, si disponible. */
    val capturedAtEpochMillis: Long?,
    /** Date de fichier MediaStore, utilisée en fallback si [capturedAtEpochMillis] est absent. */
    val fileDateEpochMillis: Long,
) {
    val effectiveDateEpochMillis: Long
        get() = capturedAtEpochMillis ?: fileDateEpochMillis
}
