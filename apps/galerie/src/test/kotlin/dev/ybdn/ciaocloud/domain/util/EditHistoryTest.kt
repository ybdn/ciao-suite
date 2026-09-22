package dev.ybdn.ciaocloud.domain.util

import dev.ybdn.ciaocloud.domain.model.Adjustment
import dev.ybdn.ciaocloud.domain.model.Adjustments
import dev.ybdn.ciaocloud.domain.model.EditRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {

    private fun recipe(brightness: Int) = EditRecipe(adjustments = Adjustments(brightness = brightness))

    @Test
    fun `undo and redo steps`() {
        val history = EditHistory()
        history.apply(recipe(10))
        history.apply(recipe(20))

        assertEquals(recipe(10), history.undo())
        assertEquals(EditRecipe(), history.undo())
        assertFalse(history.canUndo)
        assertEquals(recipe(10), history.redo())
        assertTrue(history.canRedo)
    }

    @Test
    fun `a new change clears redo`() {
        val history = EditHistory()
        history.apply(recipe(10))
        history.undo()
        history.apply(recipe(30))

        assertFalse(history.canRedo)
        assertEquals(recipe(30), history.current)
    }

    @Test
    fun `a continuous gesture is a single step`() {
        val history = EditHistory()
        history.beginGesture()
        (1..40).forEach { history.update(recipe(it)) }
        history.endGesture()

        assertEquals(recipe(40), history.current)
        assertEquals(EditRecipe(), history.undo())
        assertFalse(history.canUndo)
    }

    @Test
    fun `a gesture without change adds no step`() {
        val history = EditHistory()
        history.beginGesture()
        history.endGesture()

        assertFalse(history.canUndo)
    }

    @Test
    fun `history keeps the last 50 steps`() {
        val history = EditHistory()
        (1..60).forEach { history.apply(recipe(it)) }
        var undone = 0
        while (history.canUndo) {
            history.undo()
            undone++
        }

        assertEquals(50, undone)
        assertEquals(recipe(10), history.current)
    }

    @Test
    fun `adjustments are bounded and make the recipe non lossless`() {
        val adjustments = Adjustments.NEUTRAL.with(Adjustment.SHARPNESS, -20).with(Adjustment.CONTRAST, 250)

        assertEquals(0, adjustments.sharpness)
        assertEquals(100, adjustments.contrast)
        assertFalse(EditRecipe(adjustments = adjustments).isOrientationOnly)
        assertTrue(EditRecipe().isOrientationOnly)
    }

    @Test
    fun `reset starts a fresh session`() {
        val history = EditHistory()
        history.apply(recipe(10))
        history.reset()

        assertEquals(EditRecipe(), history.current)
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }
}
