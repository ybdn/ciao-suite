package dev.ybdn.ciaocloud.data.edit

import android.content.Context
import android.graphics.RuntimeShader
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.Adjustments
import dev.ybdn.ciaocloud.domain.model.MonoMix
import kotlin.math.hypot
import kotlin.math.max

/**
 * Shader AGSL unique des réglages, partagé par l'aperçu (`RenderEffect`) et l'export par tuiles : le
 * rendu est le même, seules les dimensions changent. Les paramètres dépendant de la taille (rayon de
 * netteté, vignettage) sont exprimés relativement au cadre recadré.
 */
class PhotoAdjustmentShader(context: Context) {

    private val source: String = context.resources.openRawResource(R.raw.photo_adjustments).bufferedReader().use { it.readText() }

    /**
     * @param frameLeft, frameTop, frameWidth, frameHeight cadre recadré dans le repère de la surface de
     * rendu (pixels).
     */
    fun create(
        adjustments: Adjustments,
        mono: MonoMix,
        frameLeft: Float,
        frameTop: Float,
        frameWidth: Float,
        frameHeight: Float,
    ): RuntimeShader = RuntimeShader(source).apply {
        fun set(name: String, value: Int) = setFloatUniform(name, value / 100f)
        setFloatUniform("mono", mono.amount)
        setFloatUniform("monoWeights", mono.red, mono.green, mono.blue)
        set("brightness", adjustments.brightness)
        set("contrast", adjustments.contrast)
        set("highlights", adjustments.highlights)
        set("shadows", adjustments.shadows)
        set("whites", adjustments.whites)
        set("blacks", adjustments.blacks)
        set("saturation", adjustments.saturation)
        set("vibrance", adjustments.vibrance)
        set("temperature", adjustments.temperature)
        set("tint", adjustments.tint)
        set("sharpness", adjustments.sharpness)
        setFloatUniform("radius", sharpenRadius(frameWidth, frameHeight))
        set("vignette", adjustments.vignette)
        setFloatUniform("frameOrigin", frameLeft, frameTop)
        setFloatUniform("frameSize", max(frameWidth, 1f), max(frameHeight, 1f))
    }

    companion object {
        /** Rayon du masque flou : fraction de la diagonale du cadre, au moins 1 pixel. */
        private const val SHARPEN_RADIUS_FRACTION = 0.0008f

        fun sharpenRadius(width: Float, height: Float): Float =
            max(1f, hypot(width, height) * SHARPEN_RADIUS_FRACTION)
    }
}
