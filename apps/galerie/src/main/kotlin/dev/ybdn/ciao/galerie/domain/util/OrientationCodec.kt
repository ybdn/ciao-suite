package dev.ybdn.ciao.galerie.domain.util

import dev.ybdn.ciao.galerie.domain.model.ImageTransform

/** Correspondance entre la balise EXIF `Orientation` (1 à 8) et la transformation d'affichage. */
object OrientationCodec {

    const val NORMAL = 1

    private val BY_EXIF = mapOf(
        1 to ImageTransform(flipped = false, rotationDegrees = 0),
        2 to ImageTransform(flipped = true, rotationDegrees = 0),
        3 to ImageTransform(flipped = false, rotationDegrees = 180),
        4 to ImageTransform(flipped = true, rotationDegrees = 180),
        5 to ImageTransform(flipped = true, rotationDegrees = 270),
        6 to ImageTransform(flipped = false, rotationDegrees = 90),
        7 to ImageTransform(flipped = true, rotationDegrees = 90),
        8 to ImageTransform(flipped = false, rotationDegrees = 270),
    )
    private val BY_TRANSFORM = BY_EXIF.entries.associate { (value, transform) -> transform to value }

    /** Valeur absente ou invalide (0, > 8) : image affichée telle quelle. */
    fun fromExif(value: Int): ImageTransform = BY_EXIF[value] ?: ImageTransform.IDENTITY

    fun toExif(transform: ImageTransform): Int = BY_TRANSFORM.getValue(transform)

    /**
     * Nouvelle valeur `Orientation` après application de [edit] (recette de l'utilisateur, exprimée sur
     * l'image affichée) à une photo d'orientation [currentExif].
     */
    fun compose(currentExif: Int, edit: ImageTransform): Int = toExif(fromExif(currentExif).then(edit))
}
