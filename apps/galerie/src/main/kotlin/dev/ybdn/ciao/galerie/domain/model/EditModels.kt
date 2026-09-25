package dev.ybdn.ciao.galerie.domain.model

import dev.ybdn.ciao.galerie.domain.util.FilterPresets

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
 * Réglages de la photo (spec v3 B3), de −100 à +100 (0 = neutre), sauf la netteté (0 à 100).
 */
data class Adjustments(
    val brightness: Int = 0,
    val contrast: Int = 0,
    val highlights: Int = 0,
    val shadows: Int = 0,
    val whites: Int = 0,
    val blacks: Int = 0,
    val saturation: Int = 0,
    val vibrance: Int = 0,
    val temperature: Int = 0,
    val tint: Int = 0,
    val sharpness: Int = 0,
    val vignette: Int = 0,
) {
    init {
        require(values().all { it in -MAX..MAX } && sharpness >= 0) { "Réglage hors bornes" }
    }

    val isNeutral: Boolean get() = this == NEUTRAL

    fun values(): IntArray = intArrayOf(
        brightness, contrast, highlights, shadows, whites, blacks,
        saturation, vibrance, temperature, tint, sharpness, vignette,
    )

    fun with(adjustment: Adjustment, value: Int): Adjustments {
        val bounded = value.coerceIn(adjustment.min, MAX)
        return fromValues(values().also { it[adjustment.ordinal] = bounded })
    }

    operator fun get(adjustment: Adjustment): Int = values()[adjustment.ordinal]

    companion object {
        const val MAX = 100
        val NEUTRAL = Adjustments()

        fun fromValues(values: IntArray): Adjustments {
            require(values.size == Adjustment.entries.size)
            return Adjustments(
                values[0], values[1], values[2], values[3], values[4], values[5],
                values[6], values[7], values[8], values[9], values[10], values[11],
            )
        }
    }
}

/** Réglage individuel, dans l'ordre de [Adjustments.values]. */
enum class Adjustment(val min: Int) {
    BRIGHTNESS(-100), CONTRAST(-100), HIGHLIGHTS(-100), SHADOWS(-100), WHITES(-100), BLACKS(-100),
    SATURATION(-100), VIBRANCE(-100), TEMPERATURE(-100), TINT(-100),
    SHARPNESS(0), VIGNETTE(-100),
}

/** Filtres prédéfinis (spec v3 B4). */
enum class FilterPreset { ORIGINAL, MONO, MONO_CONTRAST, WARM, COOL, VIVID, SOFT, FADED }

/** Mélange vers le noir et blanc : part du mélange (0 à 1) et poids des canaux. */
data class MonoMix(val amount: Float, val red: Float, val green: Float, val blue: Float) {
    companion object {
        val NONE = MonoMix(0f, 0.2126f, 0.7152f, 0.0722f)
    }
}

/**
 * Recette de retouche d'une photo, exprimée sur l'image telle qu'affichée (orientation EXIF appliquée).
 * Ordre d'application : orientation → redressement → recadrage → réglages (spec v3 B3).
 */
data class EditRecipe(
    val transform: ImageTransform = ImageTransform.IDENTITY,
    /** Redressement fin, de −45 à +45 degrés (sens horaire), pas de 0,1. */
    val straightenDegrees: Double = 0.0,
    /** Cadre normalisé dans l'image orientée et redressée. */
    val crop: NormalizedRect = NormalizedRect.FULL,
    val aspect: CropAspect = CropAspect.FREE,
    /** Réglages manuels, ajoutés à ceux du filtre. */
    val adjustments: Adjustments = Adjustments.NEUTRAL,
    val filter: FilterPreset = FilterPreset.ORIGINAL,
    /** Intensité du filtre, 0 à 100 %. */
    val filterIntensity: Int = 100,
) {
    val isIdentity: Boolean get() = transform.isIdentity && isOrientationOnly

    /** Seulement rotations et miroir : enregistrable sans réencodage pour un JPEG. */
    val isOrientationOnly: Boolean get() = straightenDegrees == 0.0 && crop.isFull && !hasPixelAdjustments

    /** Réglages appliqués au rendu : filtre × intensité + réglages manuels. */
    val effectiveAdjustments: Adjustments
        get() = FilterPresets.effectiveAdjustments(adjustments, filter, filterIntensity)

    val effectiveMono: MonoMix
        get() = FilterPresets.effectiveMono(filter, filterIntensity)

    val hasPixelAdjustments: Boolean get() = !effectiveAdjustments.isNeutral || effectiveMono.amount > 0f
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
