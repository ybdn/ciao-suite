package dev.ybdn.ciao.galerie.domain.util

import dev.ybdn.ciao.galerie.domain.model.EditAvailability
import dev.ybdn.ciao.galerie.domain.model.EditCapabilities
import dev.ybdn.ciao.galerie.domain.model.EditUnavailableReason
import dev.ybdn.ciao.galerie.domain.model.GalleryLocation
import dev.ybdn.ciao.galerie.domain.model.MediaType
import dev.ybdn.ciao.galerie.domain.repository.EncodedFormat

/** Situation d'un élément au moment de l'édition. */
data class EditContext(
    val location: GalleryLocation,
    val mediaType: MediaType,
    val mimeType: String,
    /** Dimensions de l'image si connues (limite de 200 Mpx). */
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val ssdAvailable: Boolean,
    val transferRunning: Boolean,
    /** Média hors MediaStore reçu d'une autre app (pièce jointe). */
    val isExternal: Boolean = false,
    /** Visionneuse ouverte écran verrouillé (`REVIEW_SECURE`). */
    val isLocked: Boolean = false,
)

/** Format d'image du point de vue de l'édition (spec v3 A2). */
enum class EditableFormat { JPEG, PNG, WEBP, HEIF, DNG, UNSUPPORTED }

/**
 * Tables A2 (formats) et A3 (emplacements) : actions d'édition disponibles et raison de chaque
 * indisponibilité. Un élément du SSD ou présent aux deux endroits exige le SSD branché pour toute
 * modification d'un fichier existant ; une copie d'un élément présent aux deux endroits est créée
 * sur le téléphone.
 */
object EditCapabilitiesPolicy {

    const val MAX_PIXELS = 200_000_000L

    fun formatOf(mimeType: String): EditableFormat = when (mimeType.substringBefore(';').trim().lowercase()) {
        "image/jpeg", "image/jpg", "image/pjpeg" -> EditableFormat.JPEG
        "image/png" -> EditableFormat.PNG
        "image/webp" -> EditableFormat.WEBP
        "image/heic", "image/heif", "image/avif" -> EditableFormat.HEIF
        "image/x-adobe-dng", "image/dng" -> EditableFormat.DNG
        else -> EditableFormat.UNSUPPORTED
    }

    /** Format de la photo retouchée : celui de l'original, JPEG pour un HEIC/AVIF ; null si non éditable. */
    fun outputFormat(mimeType: String): EncodedFormat? = when (formatOf(mimeType)) {
        EditableFormat.JPEG, EditableFormat.HEIF -> EncodedFormat.JPEG
        EditableFormat.PNG -> EncodedFormat.PNG
        EditableFormat.WEBP -> EncodedFormat.WEBP
        EditableFormat.DNG, EditableFormat.UNSUPPORTED -> null
    }

    fun capabilities(context: EditContext): EditCapabilities {
        blockingReason(context)?.let { reason ->
            val unavailable = EditAvailability.Unavailable(reason)
            return EditCapabilities(unavailable, unavailable, unavailable, unavailable)
        }
        val format = formatOf(context.mimeType)
        val tooLarge = context.widthPx != null && context.heightPx != null &&
            context.widthPx.toLong() * context.heightPx.toLong() > MAX_PIXELS
        val onSsd = context.location != GalleryLocation.PHONE

        fun available(formatReason: EditUnavailableReason?, needsSsd: Boolean): EditAvailability = when {
            formatReason != null -> EditAvailability.Unavailable(formatReason)
            context.transferRunning -> EditAvailability.Unavailable(EditUnavailableReason.TRANSFER_RUNNING)
            needsSsd && !context.ssdAvailable -> EditAvailability.Unavailable(EditUnavailableReason.SSD_REQUIRED)
            else -> EditAvailability.Available
        }

        val pixelFormatReason = when {
            format == EditableFormat.DNG || format == EditableFormat.UNSUPPORTED -> EditUnavailableReason.UNSUPPORTED_FORMAT
            tooLarge -> EditUnavailableReason.TOO_LARGE
            else -> null
        }
        return EditCapabilities(
            editCopy = available(pixelFormatReason, needsSsd = context.location == GalleryLocation.SSD),
            replace = available(
                pixelFormatReason ?: EditUnavailableReason.FORMAT_CHANGES_ON_SAVE.takeIf { format == EditableFormat.HEIF },
                needsSsd = onSsd,
            ),
            losslessRotation = available(
                if (format == EditableFormat.JPEG) null else EditUnavailableReason.UNSUPPORTED_FORMAT,
                needsSsd = false,
            ),
            metadata = available(
                when {
                    format == EditableFormat.UNSUPPORTED || tooLarge -> EditUnavailableReason.UNSUPPORTED_FORMAT
                    format == EditableFormat.HEIF || format == EditableFormat.DNG -> EditUnavailableReason.METADATA_READ_ONLY
                    else -> null
                },
                needsSsd = onSsd,
            ),
        )
    }

    private fun blockingReason(context: EditContext): EditUnavailableReason? = when {
        context.mediaType == MediaType.VIDEO -> EditUnavailableReason.VIDEO
        context.isExternal -> EditUnavailableReason.EXTERNAL_MEDIA
        context.isLocked -> EditUnavailableReason.LOCKED
        else -> null
    }
}
