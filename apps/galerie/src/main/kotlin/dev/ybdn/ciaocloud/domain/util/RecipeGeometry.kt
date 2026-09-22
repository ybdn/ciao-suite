package dev.ybdn.ciaocloud.domain.util

import dev.ybdn.ciaocloud.domain.model.CropAspect
import dev.ybdn.ciaocloud.domain.model.EditRecipe
import dev.ybdn.ciaocloud.domain.model.NormalizedRect

/**
 * Modifications géométriques d'une recette. [sourceWidth] × [sourceHeight] : image affichée avant
 * retouche (orientation EXIF appliquée). Le cadre suit chaque rotation et miroir, et reste toujours
 * valide pour le redressement courant.
 */
class RecipeGeometry(
    private val sourceWidth: Double,
    private val sourceHeight: Double,
) {
    /** Dimensions de l'image après les rotations et le miroir de la recette. */
    fun orientedSize(recipe: EditRecipe): Pair<Double, Double> =
        if (recipe.transform.swapsDimensions) sourceHeight to sourceWidth else sourceWidth to sourceHeight

    /** Taille en pixels du résultat. */
    fun outputSize(recipe: EditRecipe): Pair<Int, Int> {
        val (width, height) = orientedSize(recipe)
        return Math.round(recipe.crop.width * width).toInt().coerceAtLeast(1) to
            Math.round(recipe.crop.height * height).toInt().coerceAtLeast(1)
    }

    fun rotateCounterClockwise(recipe: EditRecipe): EditRecipe = recipe.copy(
        transform = recipe.transform.rotatedCounterClockwise(),
        crop = CropGeometry.rotateCounterClockwise(recipe.crop),
        aspect = recipe.aspect.rotated,
    )

    /** Le miroir inverse le sens du redressement : M·R(θ) = R(−θ)·M. */
    fun mirror(recipe: EditRecipe): EditRecipe = recipe.copy(
        transform = recipe.transform.mirroredHorizontally(),
        straightenDegrees = if (recipe.straightenDegrees == 0.0) 0.0 else -recipe.straightenDegrees,
        crop = CropGeometry.mirrorHorizontally(recipe.crop),
    )

    fun straighten(recipe: EditRecipe, degrees: Double): EditRecipe {
        val angle = Math.round(degrees.coerceIn(-CropGeometry.MAX_STRAIGHTEN_DEGREES, CropGeometry.MAX_STRAIGHTEN_DEGREES) * 10) / 10.0
        val (width, height) = orientedSize(recipe)
        return recipe.copy(straightenDegrees = angle, crop = CropGeometry.straighten(recipe.crop, width, height, angle))
    }

    fun aspect(recipe: EditRecipe, aspect: CropAspect): EditRecipe {
        val (width, height) = orientedSize(recipe)
        val crop = if (aspect == CropAspect.FREE) {
            recipe.crop
        } else {
            CropGeometry.applyAspect(recipe.crop, aspect, width, height, recipe.straightenDegrees)
        }
        return recipe.copy(aspect = aspect, crop = crop)
    }

    fun resize(recipe: EditRecipe, handle: CropHandle, dx: Double, dy: Double): EditRecipe {
        val (width, height) = orientedSize(recipe)
        return recipe.copy(crop = CropGeometry.resize(recipe.crop, handle, dx, dy, recipe.aspect, width, height, recipe.straightenDegrees))
    }

    fun move(recipe: EditRecipe, dx: Double, dy: Double): EditRecipe {
        val (width, height) = orientedSize(recipe)
        return recipe.copy(crop = CropGeometry.move(recipe.crop, dx, dy, width, height, recipe.straightenDegrees))
    }

    fun zoom(recipe: EditRecipe, zoom: Double): EditRecipe {
        val (width, height) = orientedSize(recipe)
        return recipe.copy(crop = CropGeometry.zoom(recipe.crop, zoom, width, height, recipe.straightenDegrees))
    }

    /** Géométrie remise à zéro (cadre plein, sans redressement), rotations et miroir conservés. */
    fun resetCrop(recipe: EditRecipe): EditRecipe =
        recipe.copy(straightenDegrees = 0.0, crop = NormalizedRect.FULL, aspect = CropAspect.FREE)
}
