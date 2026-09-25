package dev.ybdn.ciao.galerie.domain.model

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
    /** Instant UTC de prise de vue EXIF (photo) ou métadonnées vidéo, si disponible. */
    val capturedAtEpochMillis: Long?,
    /** Date de fichier MediaStore, utilisée en fallback si [capturedAtEpochMillis] est absent. */
    val fileDateEpochMillis: Long,
    /**
     * Décalage UTC du lieu de prise de vue en minutes, si connu (EXIF `OffsetTimeOriginal` pour
     * une photo, inféré des photos voisines pour une vidéo). Sinon, le fuseau du téléphone s'applique.
     */
    val captureUtcOffsetMinutes: Int? = null,
) {
    val effectiveDateEpochMillis: Long
        get() = capturedAtEpochMillis ?: fileDateEpochMillis
}
