package dev.ybdn.ciao.galerie.domain.util

import dev.ybdn.ciao.galerie.domain.model.ImageTransform
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Vérification par simulation de pixels, indépendante de la table du codec : une image 2×3 non
 * symétrique est transformée selon les définitions EXIF (position de la ligne 0 et de la colonne 0).
 */
private typealias Image = List<List<Int>>

class OrientationCodecTest {

    private val stored: Image = listOf(listOf(1, 2, 3), listOf(4, 5, 6))

    private fun Image.flipH(): Image = map { it.reversed() }
    private fun Image.flipV(): Image = reversed()
    private fun Image.transpose(): Image = List(this[0].size) { c -> List(size) { r -> this[r][c] } }
    private fun Image.rotateCw(): Image = transpose().flipH()
    private fun Image.rotateCcw(): Image = transpose().flipV()

    /** Affichage d'une image stockée selon la définition EXIF de chaque valeur. */
    private fun display(exif: Int, image: Image): Image = when (exif) {
        1 -> image
        2 -> image.flipH()
        3 -> image.flipH().flipV()
        4 -> image.flipV()
        5 -> image.transpose()
        6 -> image.rotateCw()
        7 -> image.rotateCw().rotateCw().transpose()
        8 -> image.rotateCcw()
        else -> error("valeur $exif")
    }

    private fun apply(transform: ImageTransform, image: Image): Image {
        var result = if (transform.flipped) image.flipH() else image
        repeat(transform.rotationDegrees / 90) { result = result.rotateCw() }
        return result
    }

    @Test
    fun `each exif value maps to the transform that displays the image correctly`() {
        (1..8).forEach { exif ->
            assertEquals("orientation $exif", display(exif, stored), apply(OrientationCodec.fromExif(exif), stored))
            assertEquals(exif, OrientationCodec.toExif(OrientationCodec.fromExif(exif)))
        }
    }

    @Test
    fun `composition covers 8 orientations x 4 rotations x mirror`() {
        for (exif in 1..8) {
            for (turns in 0..3) {
                for (mirror in listOf(false, true)) {
                    var edit = ImageTransform.IDENTITY
                    var expected = display(exif, stored)
                    repeat(turns) {
                        edit = edit.rotatedCounterClockwise()
                        expected = expected.rotateCcw()
                    }
                    if (mirror) {
                        edit = edit.mirroredHorizontally()
                        expected = expected.flipH()
                    }
                    val composed = OrientationCodec.compose(exif, edit)
                    assertEquals("exif=$exif turns=$turns mirror=$mirror", expected, display(composed, stored))
                }
            }
        }
    }

    @Test
    fun `mirror then rotations reach a vertical mirror`() {
        val vertical = ImageTransform.IDENTITY.mirroredHorizontally().rotatedCounterClockwise().rotatedCounterClockwise()
        assertEquals(4, OrientationCodec.toExif(vertical))
    }

    @Test
    fun `invalid or missing values are displayed as is`() {
        assertEquals(ImageTransform.IDENTITY, OrientationCodec.fromExif(0))
        assertEquals(ImageTransform.IDENTITY, OrientationCodec.fromExif(9))
    }

    @Test
    fun `four counter clockwise turns come back to identity`() {
        var transform = ImageTransform.IDENTITY
        repeat(4) { transform = transform.rotatedCounterClockwise() }
        assertEquals(ImageTransform.IDENTITY, transform)
        assertEquals(true, ImageTransform.IDENTITY.rotatedCounterClockwise().swapsDimensions)
    }
}
