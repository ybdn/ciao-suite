package dev.ybdn.ciao.galerie.domain.util

import dev.ybdn.ciao.galerie.domain.model.Adjustment
import dev.ybdn.ciao.galerie.domain.model.Adjustments
import dev.ybdn.ciao.galerie.domain.model.FilterPreset
import dev.ybdn.ciao.galerie.domain.model.MonoMix
import kotlin.math.roundToInt

/**
 * Valeurs effectives d'un filtre (spec v3 B4) : réglages du filtre × intensité + réglages manuels,
 * bornés ; le mélange noir et blanc suit l'intensité. Changer de filtre ne touche pas aux réglages
 * manuels.
 */
object FilterPresets {

    fun presetAdjustments(preset: FilterPreset): Adjustments = when (preset) {
        FilterPreset.ORIGINAL, FilterPreset.MONO -> Adjustments.NEUTRAL
        FilterPreset.MONO_CONTRAST -> Adjustments(contrast = 40, whites = 15, blacks = -20)
        FilterPreset.WARM -> Adjustments(temperature = 35, tint = 5, vibrance = 10)
        FilterPreset.COOL -> Adjustments(temperature = -35, tint = -5)
        FilterPreset.VIVID -> Adjustments(contrast = 15, saturation = 20, vibrance = 35)
        FilterPreset.SOFT -> Adjustments(contrast = -25, highlights = -20, shadows = 15, saturation = -10)
        FilterPreset.FADED -> Adjustments(contrast = -25, whites = -10, blacks = 40, saturation = -30)
    }

    fun presetMono(preset: FilterPreset): MonoMix = when (preset) {
        FilterPreset.MONO -> MonoMix(1f, 0.2126f, 0.7152f, 0.0722f)
        // Canal rouge renforcé : ciels plus sombres, contraste plus marqué.
        FilterPreset.MONO_CONTRAST -> MonoMix(1f, 0.40f, 0.50f, 0.10f)
        else -> MonoMix.NONE
    }

    /** @param intensity 0 à 100 (%). */
    fun effectiveAdjustments(manual: Adjustments, preset: FilterPreset, intensity: Int): Adjustments {
        val factor = intensity.coerceIn(0, 100) / 100.0
        val filter = presetAdjustments(preset).values()
        val manualValues = manual.values()
        val combined = IntArray(manualValues.size) { index ->
            val adjustment = Adjustment.entries[index]
            ((filter[index] * factor).roundToInt() + manualValues[index]).coerceIn(adjustment.min, Adjustments.MAX)
        }
        return Adjustments.fromValues(combined)
    }

    fun effectiveMono(preset: FilterPreset, intensity: Int): MonoMix {
        val mix = presetMono(preset)
        return mix.copy(amount = mix.amount * intensity.coerceIn(0, 100) / 100f)
    }
}
