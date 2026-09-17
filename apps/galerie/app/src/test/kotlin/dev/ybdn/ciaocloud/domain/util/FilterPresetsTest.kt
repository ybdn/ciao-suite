package dev.ybdn.ciaocloud.domain.util

import dev.ybdn.ciaocloud.domain.model.Adjustments
import dev.ybdn.ciaocloud.domain.model.EditRecipe
import dev.ybdn.ciaocloud.domain.model.FilterPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterPresetsTest {

    @Test
    fun `intensity scales the filter values`() {
        assertEquals(Adjustments.NEUTRAL, FilterPresets.effectiveAdjustments(Adjustments.NEUTRAL, FilterPreset.WARM, 0))
        assertEquals(18, FilterPresets.effectiveAdjustments(Adjustments.NEUTRAL, FilterPreset.WARM, 50).temperature)
        assertEquals(35, FilterPresets.effectiveAdjustments(Adjustments.NEUTRAL, FilterPreset.WARM, 100).temperature)
    }

    @Test
    fun `manual adjustments are added and bounded`() {
        val manual = Adjustments(temperature = 80, contrast = -10)
        val effective = FilterPresets.effectiveAdjustments(manual, FilterPreset.WARM, 100)

        assertEquals(100, effective.temperature)
        assertEquals(-10, effective.contrast)
        assertEquals(-100, FilterPresets.effectiveAdjustments(Adjustments(saturation = -90), FilterPreset.FADED, 100).saturation)
    }

    @Test
    fun `black and white mix follows intensity`() {
        assertEquals(1f, FilterPresets.effectiveMono(FilterPreset.MONO, 100).amount)
        assertEquals(0.5f, FilterPresets.effectiveMono(FilterPreset.MONO_CONTRAST, 50).amount)
        assertEquals(0f, FilterPresets.effectiveMono(FilterPreset.VIVID, 100).amount)
    }

    @Test
    fun `a filter makes the recipe non lossless unless its intensity is zero`() {
        assertFalse(EditRecipe(filter = FilterPreset.MONO).isOrientationOnly)
        assertTrue(EditRecipe(filter = FilterPreset.MONO, filterIntensity = 0).isOrientationOnly)
        assertTrue(EditRecipe(filter = FilterPreset.ORIGINAL).isIdentity)
    }

    @Test
    fun `changing the filter keeps manual adjustments`() {
        val recipe = EditRecipe(adjustments = Adjustments(brightness = 20), filter = FilterPreset.COOL)
        val changed = recipe.copy(filter = FilterPreset.VIVID)

        assertEquals(20, changed.effectiveAdjustments.brightness)
        assertEquals(0, changed.effectiveAdjustments.temperature)
    }
}
