package dev.ybdn.ciaocloud.domain.model

import java.time.LocalDate

/** Média présent sur le téléphone, tel que lu (colonnes légères) pour la galerie. */
data class PhoneMedia(
    val mediaStoreId: Long,
    val uri: String,
    val displayName: String,
    val mediaType: MediaType,
    val mimeType: String,
    val sizeBytes: Long,
    /** Instant de prise de vue MediaStore (`DATE_TAKEN`), à défaut date de modification du fichier. */
    val takenAtEpochMillis: Long,
    val dateModifiedEpochMillis: Long,
    val width: Int,
    val height: Int,
    val durationMillis: Long?,
    /** Dossier MediaStore, ex. "DCIM/Camera/". */
    val relativePath: String?,
)

/** Média archivé sur le SSD, connu par l'index local (consultable SSD débranché). */
data class SsdMedia(
    /** Chemin relatif à la racine du SSD, ex. "DCIM/2025/04/21/IMG_1234.jpg". */
    val relativePath: String,
    val displayName: String,
    val mediaType: MediaType,
    val mimeType: String,
    val sizeBytes: Long,
    /** Jour local de prise de vue, déduit du chemin `DCIM/aaaa/MM/jj`. */
    val captureDate: LocalDate,
    /** Instant de prise de vue s'il est connu (média transféré par l'app), sinon null. */
    val capturedAtEpochMillis: Long?,
    val lastModifiedEpochMillis: Long,
)

enum class GalleryLocation { PHONE, SSD, BOTH }

/** Élément de la chronologie unifiée téléphone + SSD. */
data class GalleryItem(
    /** Identifiant d'affichage : `phone:<id>` tant que le média est sur le téléphone, sinon `ssd:<chemin>`. */
    val key: String,
    val phone: PhoneMedia?,
    val ssd: SsdMedia?,
    val captureDate: LocalDate,
    /** Instant servant au tri dans la journée, null si inconnu (tri par nom). */
    val sortEpochMillis: Long?,
    val isFavorite: Boolean = false,
) {
    init {
        require(phone != null || ssd != null) { "Un élément de galerie est sur le téléphone, le SSD ou les deux" }
    }

    val location: GalleryLocation
        get() = when {
            phone != null && ssd != null -> GalleryLocation.BOTH
            phone != null -> GalleryLocation.PHONE
            else -> GalleryLocation.SSD
        }

    val mediaType: MediaType get() = phone?.mediaType ?: ssd!!.mediaType
    val displayName: String get() = phone?.displayName ?: ssd!!.displayName
    val mimeType: String get() = phone?.mimeType ?: ssd!!.mimeType
    val sizeBytes: Long get() = phone?.sizeBytes ?: ssd!!.sizeBytes

    /** Clé de favori stable : suit le média sur le SSD pour survivre à sa suppression du téléphone. */
    val favoriteKey: String
        get() = ssd?.let { FavoriteKeys.ssd(it.relativePath) } ?: FavoriteKeys.phone(phone!!.mediaStoreId)
}

object FavoriteKeys {
    fun phone(mediaStoreId: Long): String = "phone:$mediaStoreId"
    fun ssd(relativePath: String): String = "ssd:$relativePath"
}

enum class GalleryFilter { ALL, FAVORITES, NOT_BACKED_UP, VIDEOS }

/** Jour de la chronologie et ses médias, du plus récent au plus ancien. */
data class TimelineDay(
    val date: LocalDate,
    val items: List<GalleryItem>,
)
