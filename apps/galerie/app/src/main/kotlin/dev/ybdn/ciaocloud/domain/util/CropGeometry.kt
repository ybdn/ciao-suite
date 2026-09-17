package dev.ybdn.ciaocloud.domain.util

import dev.ybdn.ciaocloud.domain.model.CropAspect
import dev.ybdn.ciaocloud.domain.model.NormalizedRect
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Géométrie du recadrage. Repère : image orientée (EXIF, rotations et miroir de la recette appliqués)
 * de taille [width] × [height], pivotée de l'angle de redressement (degrés, sens horaire) autour de
 * son centre. Le cadre est un rectangle aligné sur les axes, normalisé dans ce repère (0..1), qui doit
 * rester entièrement dans l'image pivotée (jamais de coins vides).
 */
object CropGeometry {

    const val MIN_SIZE_PX = 64.0
    const val MAX_STRAIGHTEN_DEGREES = 45.0
    private const val EPSILON = 1e-9

    /**
     * Marge (pixels source) entre le cadre et le bord de l'image pivotée : le filtrage bilinéaire du
     * bord mélangerait sinon l'image au fond transparent (coin sombre).
     */
    const val EDGE_MARGIN_PX = 2.0
    private const val SEARCH_STEPS = 30

    /** Rapport largeur/hauteur d'une proportion, null pour « Libre ». */
    fun ratio(aspect: CropAspect, width: Double, height: Double): Double? = when (aspect) {
        CropAspect.FREE -> null
        CropAspect.ORIGINAL -> width / height
        else -> aspect.widthRatio!!.toDouble() / aspect.heightRatio!!
    }

    /**
     * Plus grand cadre de rapport [ratio] (en pixels, null = celui de l'image) centré dans l'image
     * pivotée de [angleDegrees].
     */
    fun largestInscribed(width: Double, height: Double, angleDegrees: Double, ratio: Double? = null): NormalizedRect {
        val r = ratio ?: (width / height)
        val radians = Math.toRadians(angleDegrees)
        val c = abs(cos(radians))
        val s = abs(sin(radians))
        // Demi-hauteur maximale : coins ramenés dans le repère de l'image non pivotée.
        val margin = if (angleDegrees == 0.0) 0.0 else EDGE_MARGIN_PX
        val halfHeight = min((width / 2 - margin) / (r * c + s), (height / 2 - margin) / (r * s + c))
        val halfWidth = halfHeight * r
        return NormalizedRect(
            left = 0.5 - halfWidth / width,
            top = 0.5 - halfHeight / height,
            right = 0.5 + halfWidth / width,
            bottom = 0.5 + halfHeight / height,
        )
    }

    /** true si les quatre coins du cadre sont dans l'image pivotée. */
    fun isInside(rect: NormalizedRect, width: Double, height: Double, angleDegrees: Double): Boolean {
        val radians = Math.toRadians(-angleDegrees)
        val c = cos(radians)
        val s = sin(radians)
        val corners = listOf(rect.left to rect.top, rect.right to rect.top, rect.right to rect.bottom, rect.left to rect.bottom)
        val margin = if (angleDegrees == 0.0) 0.0 else EDGE_MARGIN_PX
        return corners.all { (nx, ny) ->
            val x = (nx - 0.5) * width
            val y = (ny - 0.5) * height
            val rx = x * c - y * s
            val ry = x * s + y * c
            abs(rx) <= width / 2 - margin + EPSILON * width && abs(ry) <= height / 2 - margin + EPSILON * height
        }
    }

    /**
     * Ramène le cadre dans l'image pivotée : centre replacé au centre de l'image s'il en sort, puis
     * réduction autour de son centre (proportion conservée) jusqu'au plus grand cadre qui tient.
     */
    fun constrain(rect: NormalizedRect, width: Double, height: Double, angleDegrees: Double): NormalizedRect {
        if (isInside(rect, width, height, angleDegrees)) return rect
        val centered = if (isInside(rect.scaledAboutCenter(0.0), width, height, angleDegrees)) {
            rect
        } else {
            rect.translated(0.5 - rect.centerX, 0.5 - rect.centerY)
        }
        val scale = searchLargest { isInside(centered.scaledAboutCenter(it), width, height, angleDegrees) }
        return centered.scaledAboutCenter(scale)
    }

    /** Taille minimale de 64 px de l'image source sur chaque côté (agrandi autour du centre si besoin). */
    fun enforceMinSize(rect: NormalizedRect, width: Double, height: Double): NormalizedRect {
        val minWidth = min(1.0, MIN_SIZE_PX / width)
        val minHeight = min(1.0, MIN_SIZE_PX / height)
        val newWidth = max(rect.width, minWidth)
        val newHeight = max(rect.height, minHeight)
        return NormalizedRect.centered(rect.centerX, rect.centerY, newWidth, newHeight)
    }

    fun isLargeEnough(rect: NormalizedRect, width: Double, height: Double): Boolean =
        rect.width * width >= MIN_SIZE_PX - EPSILON && rect.height * height >= MIN_SIZE_PX - EPSILON

    /**
     * Applique une proportion : plus grand cadre de ce rapport, centré sur le cadre actuel, puis
     * contraint dans l'image pivotée. « Libre » ne change rien.
     */
    fun applyAspect(rect: NormalizedRect, aspect: CropAspect, width: Double, height: Double, angleDegrees: Double): NormalizedRect {
        val ratio = ratio(aspect, width, height) ?: return rect
        val maximal = largestInscribed(width, height, angleDegrees, ratio)
        val moved = maximal.translated(rect.centerX - maximal.centerX, rect.centerY - maximal.centerY)
        return constrain(moved, width, height, angleDegrees)
    }

    /** Nouveau redressement : le cadre reste valide (réduit si besoin au plus grand rectangle inscrit). */
    fun straighten(rect: NormalizedRect, width: Double, height: Double, angleDegrees: Double): NormalizedRect =
        constrain(rect, width, height, angleDegrees.coerceIn(-MAX_STRAIGHTEN_DEGREES, MAX_STRAIGHTEN_DEGREES))

    /**
     * Déplacement du cadre de ([dx], [dy]) (fractions de l'image). Le mouvement est réduit au plus grand
     * déplacement qui garde le cadre dans l'image pivotée.
     */
    fun move(rect: NormalizedRect, dx: Double, dy: Double, width: Double, height: Double, angleDegrees: Double): NormalizedRect {
        val fraction = searchLargest { isInside(rect.translated(dx * it, dy * it), width, height, angleDegrees) }
        return rect.translated(dx * fraction, dy * fraction)
    }

    /**
     * Redimensionne en déplaçant les bords désignés par [handle] de ([dx], [dy]). Avec une proportion
     * fixe, la hauteur suit la largeur (ou l'inverse pour une poignée de bord horizontal), ancrée sur le
     * côté opposé. Le mouvement est limité à ce qui respecte taille minimale et image pivotée.
     */
    fun resize(
        rect: NormalizedRect,
        handle: CropHandle,
        dx: Double,
        dy: Double,
        aspect: CropAspect,
        width: Double,
        height: Double,
        angleDegrees: Double,
    ): NormalizedRect {
        val ratio = ratio(aspect, width, height)
        fun limited(from: NormalizedRect, moveX: Double, moveY: Double): NormalizedRect {
            fun candidate(t: Double) = resized(from, handle, moveX * t, moveY * t, ratio, width, height)
            val fraction = searchLargest { t ->
                val next = candidate(t)
                next.isValid && isLargeEnough(next, width, height) && isInside(next, width, height, angleDegrees)
            }
            return if (fraction <= 0.0) from else candidate(fraction)
        }
        // Proportion libre : chaque axe est limité indépendamment (un bord bloqué n'arrête pas l'autre).
        return if (ratio == null) limited(limited(rect, dx, 0.0), 0.0, dy) else limited(rect, dx, dy)
    }

    /**
     * Pincement : l'image est agrandie sous le cadre, c'est-à-dire que le cadre rétrécit (zoom > 1) ou
     * s'agrandit (zoom < 1) autour de son centre, dans les limites de la taille minimale et de l'image.
     */
    fun zoom(rect: NormalizedRect, zoom: Double, width: Double, height: Double, angleDegrees: Double): NormalizedRect {
        if (zoom <= 0.0) return rect
        val scaled = rect.scaledAboutCenter(1.0 / zoom)
        if (zoom > 1.0) {
            return if (isLargeEnough(scaled, width, height)) scaled else rect
        }
        val fraction = searchLargest { t ->
            isInside(rect.scaledAboutCenter(1.0 + (1.0 / zoom - 1.0) * t), width, height, angleDegrees)
        }
        return rect.scaledAboutCenter(1.0 + (1.0 / zoom - 1.0) * fraction)
    }

    /** Cadre après un quart de tour anti-horaire de l'image. */
    fun rotateCounterClockwise(rect: NormalizedRect): NormalizedRect =
        NormalizedRect(left = rect.top, top = 1 - rect.right, right = rect.bottom, bottom = 1 - rect.left)

    /** Cadre après un miroir horizontal de l'image. */
    fun mirrorHorizontally(rect: NormalizedRect): NormalizedRect =
        NormalizedRect(left = 1 - rect.right, top = rect.top, right = 1 - rect.left, bottom = rect.bottom)

    private fun resized(
        rect: NormalizedRect,
        handle: CropHandle,
        dx: Double,
        dy: Double,
        ratio: Double?,
        width: Double,
        height: Double,
    ): NormalizedRect {
        var left = rect.left + if (handle.left) dx else 0.0
        var right = rect.right + if (handle.right) dx else 0.0
        var top = rect.top + if (handle.top) dy else 0.0
        var bottom = rect.bottom + if (handle.bottom) dy else 0.0
        if (ratio == null) return NormalizedRect(left, top, right, bottom)

        // Rapport en coordonnées normalisées : (w × width) / (h × height) = ratio.
        val normalizedRatio = ratio * height / width
        val horizontalDriven = handle.left || handle.right
        if (horizontalDriven) {
            val newHeight = (right - left) / normalizedRatio
            when {
                handle.top -> top = bottom - newHeight
                handle.bottom -> bottom = top + newHeight
                else -> {
                    val centerY = rect.centerY
                    top = centerY - newHeight / 2
                    bottom = centerY + newHeight / 2
                }
            }
        } else {
            val newWidth = (bottom - top) * normalizedRatio
            val centerX = rect.centerX
            left = centerX - newWidth / 2
            right = centerX + newWidth / 2
        }
        return NormalizedRect(left, top, right, bottom)
    }

    /** Plus grand t ∈ [0, 1] satisfaisant [valid] (supposé vrai en 0 et monotone), par dichotomie. */
    private fun searchLargest(valid: (Double) -> Boolean): Double {
        if (valid(1.0)) return 1.0
        var low = 0.0
        var high = 1.0
        repeat(SEARCH_STEPS) {
            val mid = (low + high) / 2
            if (valid(mid)) low = mid else high = mid
        }
        return low
    }
}

/** Poignée du cadre : coins, bords, ou déplacement de tout le cadre (aucun bord). */
enum class CropHandle(val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean) {
    TOP_LEFT(true, true, false, false),
    TOP(false, true, false, false),
    TOP_RIGHT(false, true, true, false),
    RIGHT(false, false, true, false),
    BOTTOM_RIGHT(false, false, true, true),
    BOTTOM(false, false, false, true),
    BOTTOM_LEFT(true, false, false, true),
    LEFT(true, false, false, false),
}
