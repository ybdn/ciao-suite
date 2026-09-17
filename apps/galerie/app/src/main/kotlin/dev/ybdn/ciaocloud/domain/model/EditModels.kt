package dev.ybdn.ciaocloud.domain.model

/**
 * Transformation d'orientation (groupe du carré : 4 rotations × miroir). Appliquée à une image, elle
 * la retourne d'abord horizontalement si [flipped], puis la fait pivoter de [rotationDegrees] dans le
 * sens horaire (convention `ExifInterface.isFlipped` / `getRotationDegrees`).
 */
data class ImageTransform(
    val flipped: Boolean = false,
    /** 0, 90, 180 ou 270. */
    val rotationDegrees: Int = 0,
) {
    init {
        require(rotationDegrees in VALID_ROTATIONS) { "Rotation invalide : $rotationDegrees" }
    }

    val isIdentity: Boolean get() = !flipped && rotationDegrees == 0

    /** Largeur et hauteur échangées (quart de tour). */
    val swapsDimensions: Boolean get() = rotationDegrees % 180 != 0

    /** Transformation qui applique d'abord `this`, puis [next]. */
    fun then(next: ImageTransform): ImageTransform {
        // R(a)·F^fa · R(b)·F^fb = R(a ± b)·F^(fa xor fb), car F·R(b) = R(−b)·F.
        val rotation = next.rotationDegrees + if (next.flipped) -rotationDegrees else rotationDegrees
        return ImageTransform(flipped = next.flipped != flipped, rotationDegrees = Math.floorMod(rotation, 360))
    }

    /** Quart de tour dans le sens anti-horaire, appliqué à l'image telle qu'affichée. */
    fun rotatedCounterClockwise(): ImageTransform = then(ImageTransform(rotationDegrees = 270))

    /** Miroir horizontal de l'image telle qu'affichée. */
    fun mirroredHorizontally(): ImageTransform = then(ImageTransform(flipped = true))

    companion object {
        // Déclarée avant IDENTITY, dont la construction la lit.
        private val VALID_ROTATIONS = setOf(0, 90, 180, 270)
        val IDENTITY = ImageTransform()
    }
}

/** Rectangle normalisé (0..1) dans le repère d'une image. */
data class NormalizedRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val centerX: Double get() = (left + right) / 2
    val centerY: Double get() = (top + bottom) / 2
    val isValid: Boolean get() = right > left && bottom > top

    val isFull: Boolean
        get() = left <= TOLERANCE && top <= TOLERANCE && right >= 1 - TOLERANCE && bottom >= 1 - TOLERANCE

    fun translated(dx: Double, dy: Double) = NormalizedRect(left + dx, top + dy, right + dx, bottom + dy)

    fun scaledAboutCenter(scale: Double) = centered(centerX, centerY, width * scale, height * scale)

    companion object {
        val FULL = NormalizedRect(0.0, 0.0, 1.0, 1.0)
        private const val TOLERANCE = 1e-6

        fun centered(centerX: Double, centerY: Double, width: Double, height: Double) =
            NormalizedRect(centerX - width / 2, centerY - height / 2, centerX + width / 2, centerY + height / 2)
    }
}

/** Proportions du cadre (spec v3 B2). */
enum class CropAspect(val widthRatio: Int?, val heightRatio: Int?) {
    FREE(null, null),
    ORIGINAL(null, null),
    SQUARE(1, 1),
    FOUR_THREE(4, 3),
    THREE_FOUR(3, 4),
    SIXTEEN_NINE(16, 9),
    NINE_SIXTEEN(9, 16),
    THREE_TWO(3, 2),
    TWO_THREE(2, 3),
    ;

    /** Proportion après un quart de tour (4:3 → 3:4). */
    val rotated: CropAspect
        get() = entries.firstOrNull { it.widthRatio != null && it.widthRatio == heightRatio && it.heightRatio == widthRatio } ?: this
}

/**
 * Recette de retouche d'une photo, exprimée sur l'image telle qu'affichée (orientation EXIF appliquée).
 * Ordre d'application : orientation → redressement → recadrage (spec v3 B3).
 */
data class EditRecipe(
    val transform: ImageTransform = ImageTransform.IDENTITY,
    /** Redressement fin, de −45 à +45 degrés (sens horaire), pas de 0,1. */
    val straightenDegrees: Double = 0.0,
    /** Cadre normalisé dans l'image orientée et redressée. */
    val crop: NormalizedRect = NormalizedRect.FULL,
    val aspect: CropAspect = CropAspect.FREE,
) {
    val isIdentity: Boolean get() = transform.isIdentity && isOrientationOnly

    /** Seulement rotations et miroir : enregistrable sans réencodage pour un JPEG. */
    val isOrientationOnly: Boolean get() = straightenDegrees == 0.0 && crop.isFull
}

enum class SaveMode { COPY, REPLACE }

/** Pourquoi une action d'édition est indisponible. */
enum class EditUnavailableReason {
    VIDEO,
    EXTERNAL_MEDIA,
    LOCKED,
    UNSUPPORTED_FORMAT,
    TOO_LARGE,

    /** Métadonnées lisibles mais non modifiables (HEIC, AVIF, DNG). */
    METADATA_READ_ONLY,

    /** La copie change de format (HEIC → JPEG) : l'original ne peut pas être remplacé. */
    FORMAT_CHANGES_ON_SAVE,
    TRANSFER_RUNNING,
    SSD_REQUIRED,
    ;

    /** Action masquée (sans objet pour ce média) plutôt que désactivée avec un message. */
    val hidesAction: Boolean get() = this != TRANSFER_RUNNING && this != SSD_REQUIRED
}

sealed interface EditAvailability {
    data object Available : EditAvailability
    data class Unavailable(val reason: EditUnavailableReason) : EditAvailability

    val isAvailable: Boolean get() = this == Available
}

/** Actions d'édition possibles pour un élément (spec v3 A2/A3). */
data class EditCapabilities(
    /** Ouvrir l'éditeur et enregistrer une copie retouchée. */
    val editCopy: EditAvailability,
    /** Remplacer l'original par la version retouchée. */
    val replace: EditAvailability,
    /** Enregistrer rotations et miroir sans réencodage (balise `Orientation`). */
    val losslessRotation: EditAvailability,
    /** Modifier les métadonnées en place. */
    val metadata: EditAvailability,
)

/** Taille et CRC32 (hexadécimal sur 8 caractères, comme les enregistrements de transfert) d'un fichier. */
data class FileFingerprint(
    val sizeBytes: Long,
    val checksum: String,
)

sealed interface SaveEditOutcome {
    /** Copie créée ; [key] : clé de chronologie de la copie. */
    data class Copied(val key: String) : SaveEditOutcome

    data object Replaced : SaveEditOutcome

    data object NothingToSave : SaveEditOutcome

    /** Droit d'écriture refusé par l'utilisateur : rien n'a été modifié. */
    data object Cancelled : SaveEditOutcome

    data class Unavailable(val reason: EditUnavailableReason) : SaveEditOutcome

    data object SsdUnavailable : SaveEditOutcome

    data class Failed(val message: String) : SaveEditOutcome
}
