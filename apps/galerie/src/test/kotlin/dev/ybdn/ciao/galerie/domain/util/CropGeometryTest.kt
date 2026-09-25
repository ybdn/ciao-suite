package dev.ybdn.ciao.galerie.domain.util

import dev.ybdn.ciao.galerie.domain.model.CropAspect
import dev.ybdn.ciao.galerie.domain.model.EditRecipe
import dev.ybdn.ciao.galerie.domain.model.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropGeometryTest {

    private val width = 4000.0
    private val height = 3000.0

    private fun pixelRatio(rect: NormalizedRect, w: Double = width, h: Double = height) = rect.width * w / (rect.height * h)

    @Test
    fun `without straightening the full image is inscribed`() {
        val rect = CropGeometry.largestInscribed(width, height, 0.0)
        assertEquals(0.0, rect.left, 1e-9)
        assertEquals(1.0, rect.bottom, 1e-9)
    }

    @Test
    fun `straightened frame stays inside the rotated image for any angle up to 45 degrees`() {
        for (angle in listOf(-45.0, -30.0, -12.3, -0.1, 0.1, 7.5, 30.0, 45.0)) {
            val rect = CropGeometry.straighten(NormalizedRect.FULL, width, height, angle)
            assertTrue("angle $angle", CropGeometry.isInside(rect, width, height, angle))
            assertFalse("angle $angle", rect.isFull)
            // Proportion conservée : le cadre plein est réduit autour du centre.
            assertEquals(width / height, pixelRatio(rect), 1e-6)
            assertEquals(0.5, rect.centerX, 1e-9)
        }
    }

    @Test
    fun `the inscribed frame is the largest one`() {
        val angle = 20.0
        val rect = CropGeometry.largestInscribed(width, height, angle)
        assertTrue(CropGeometry.isInside(rect, width, height, angle))
        assertFalse(CropGeometry.isInside(rect.scaledAboutCenter(1.01), width, height, angle))
    }

    @Test
    fun `an off-center frame is pulled back inside`() {
        val rect = NormalizedRect(0.7, 0.7, 1.0, 1.0)
        val constrained = CropGeometry.constrain(rect, width, height, 30.0)
        assertTrue(CropGeometry.isInside(constrained, width, height, 30.0))
    }

    @Test
    fun `applying an aspect keeps the requested ratio`() {
        for (aspect in listOf(CropAspect.SQUARE, CropAspect.SIXTEEN_NINE, CropAspect.NINE_SIXTEEN, CropAspect.TWO_THREE)) {
            val rect = CropGeometry.applyAspect(NormalizedRect.FULL, aspect, width, height, 10.0)
            assertEquals(aspect.name, aspect.widthRatio!!.toDouble() / aspect.heightRatio!!, pixelRatio(rect), 1e-6)
            assertTrue(CropGeometry.isInside(rect, width, height, 10.0))
        }
        assertEquals(width / height, pixelRatio(CropGeometry.applyAspect(NormalizedRect(0.2, 0.2, 0.4, 0.9), CropAspect.ORIGINAL, width, height, 0.0)), 1e-6)
    }

    @Test
    fun `a corner drag with a fixed aspect keeps the ratio and the opposite corner`() {
        val start = CropGeometry.applyAspect(NormalizedRect.FULL, CropAspect.SQUARE, width, height, 0.0)
        val resized = CropGeometry.resize(start, CropHandle.TOP_LEFT, 0.1, 0.0, CropAspect.SQUARE, width, height, 0.0)
        assertEquals(1.0, pixelRatio(resized), 1e-6)
        assertEquals(start.right, resized.right, 1e-9)
        assertEquals(start.bottom, resized.bottom, 1e-9)
        assertTrue(resized.width < start.width)
    }

    @Test
    fun `a drag cannot push the frame out of the image`() {
        val resized = CropGeometry.resize(NormalizedRect.FULL, CropHandle.RIGHT, 0.2, 0.0, CropAspect.FREE, width, height, 0.0)
        assertEquals(1.0, resized.right, 1e-6)
        val moved = CropGeometry.move(NormalizedRect(0.2, 0.2, 0.6, 0.6), 0.9, 0.0, width, height, 0.0)
        assertEquals(1.0, moved.right, 1e-6)
        assertEquals(0.6, moved.left, 1e-6)
    }

    @Test
    fun `the frame never gets smaller than 64 source pixels`() {
        val resized = CropGeometry.resize(NormalizedRect.FULL, CropHandle.BOTTOM_RIGHT, -0.999, -0.999, CropAspect.FREE, width, height, 0.0)
        assertEquals(64.0, resized.width * width, 0.5)
        assertEquals(64.0, resized.height * height, 0.5)
        val tiny = CropGeometry.zoom(NormalizedRect.centered(0.5, 0.5, 64 / width, 64 / height), 4.0, width, height, 0.0)
        assertEquals(64.0, tiny.width * width, 1e-6)
        val enforced = CropGeometry.enforceMinSize(NormalizedRect.centered(0.5, 0.5, 0.001, 0.001), width, height)
        assertTrue(CropGeometry.isLargeEnough(enforced, width, height))
    }

    @Test
    fun `pinching zooms the image under the frame`() {
        val zoomedIn = CropGeometry.zoom(NormalizedRect.FULL, 2.0, width, height, 0.0)
        assertEquals(0.5, zoomedIn.width, 1e-9)
        val zoomedOut = CropGeometry.zoom(zoomedIn, 0.25, width, height, 0.0)
        assertTrue(zoomedOut.isFull)
    }

    @Test
    fun `a quarter turn rotates the frame and inverts the aspect`() {
        val geometry = RecipeGeometry(width, height)
        val recipe = geometry.aspect(EditRecipe(), CropAspect.FOUR_THREE).let { geometry.resize(it, CropHandle.LEFT, 0.2, 0.0) }
        val rotated = geometry.rotateCounterClockwise(recipe)

        assertEquals(CropAspect.THREE_FOUR, rotated.aspect)
        val (rotatedWidth, rotatedHeight) = geometry.orientedSize(rotated)
        assertEquals(3000.0, rotatedWidth, 0.0)
        assertEquals(pixelRatio(recipe.crop), 1 / pixelRatio(rotated.crop, rotatedWidth, rotatedHeight), 1e-6)
        assertEquals(geometry.outputSize(recipe).first, geometry.outputSize(rotated).second)
        // Quatre quarts de tour : retour à l'identique.
        var back = rotated
        repeat(3) { back = geometry.rotateCounterClockwise(back) }
        assertEquals(recipe.crop.left, back.crop.left, 1e-9)
        assertEquals(recipe.aspect, back.aspect)
    }

    @Test
    fun `a mirror inverts straightening and mirrors the frame`() {
        val geometry = RecipeGeometry(width, height)
        val recipe = geometry.straighten(EditRecipe(), 12.34)
            .let { geometry.move(it, -0.01, 0.0) }
        val mirrored = geometry.mirror(recipe)

        assertEquals(12.3, recipe.straightenDegrees, 0.0)
        assertEquals(-12.3, mirrored.straightenDegrees, 0.0)
        assertEquals(1 - recipe.crop.right, mirrored.crop.left, 1e-9)
        assertTrue(CropGeometry.isInside(mirrored.crop, width, height, mirrored.straightenDegrees))
        assertFalse(mirrored.isOrientationOnly)
        assertTrue(geometry.resetCrop(mirrored).isOrientationOnly)
    }

    @Test
    fun `straightening is limited to 45 degrees`() {
        assertEquals(45.0, RecipeGeometry(width, height).straighten(EditRecipe(), 60.0).straightenDegrees, 0.0)
    }
}
