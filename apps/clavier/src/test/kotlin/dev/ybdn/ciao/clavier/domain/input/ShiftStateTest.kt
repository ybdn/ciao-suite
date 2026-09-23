package dev.ybdn.ciao.clavier.domain.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftStateTest {

    @Test
    fun `without auto capitalization, a tap turns the shift on then off`() {
        val afterFirstTap = ShiftState.Off.onShiftTap(EffectiveShift.Off, isDoubleTap = false)
        assertEquals(ShiftState.Shift, afterFirstTap)
        assertEquals(EffectiveShift.Shift, afterFirstTap.effective(autoCapitalize = false))

        val afterSecondTap = afterFirstTap.onShiftTap(EffectiveShift.Shift, isDoubleTap = false)
        assertEquals(ShiftState.Off, afterSecondTap)
    }

    @Test
    fun `a double tap locks, and a tap unlocks`() {
        val locked = ShiftState.Off.onShiftTap(EffectiveShift.Off, isDoubleTap = true)
        assertEquals(ShiftState.CapsLock, locked)
        assertEquals(ShiftState.Off, locked.onShiftTap(EffectiveShift.CapsLock, isDoubleTap = false))
        assertEquals(ShiftState.Off, locked.onShiftTap(EffectiveShift.CapsLock, isDoubleTap = true))
    }

    @Test
    fun `the field asking for capitalization turns the shift on by itself`() {
        assertEquals(EffectiveShift.Auto, ShiftState.Off.effective(autoCapitalize = true))
        assertEquals(EffectiveShift.Off, ShiftState.Off.effective(autoCapitalize = false))
    }

    @Test
    fun `tapping the shift refuses the automatic capitalization for this position only`() {
        val refused = ShiftState.Off.onShiftTap(EffectiveShift.Auto, isDoubleTap = false)
        assertEquals(ShiftState.AutoDisabled, refused)
        assertEquals(EffectiveShift.Off, refused.effective(autoCapitalize = true))

        // Une fois le caractère tapé, la position suivante est réévaluée.
        assertEquals(EffectiveShift.Auto, refused.afterCharacterTyped().effective(autoCapitalize = true))
    }

    @Test
    fun `automatic capitalization can still be locked with a double tap`() {
        assertEquals(
            ShiftState.CapsLock,
            ShiftState.Off.onShiftTap(EffectiveShift.Auto, isDoubleTap = true),
        )
    }

    @Test
    fun `a one-off shift falls back after a character, the lock stays`() {
        assertEquals(ShiftState.Off, ShiftState.Shift.afterCharacterTyped())
        assertEquals(ShiftState.CapsLock, ShiftState.CapsLock.afterCharacterTyped())
        assertEquals(ShiftState.Off, ShiftState.Off.afterCharacterTyped())
    }

    @Test
    fun `isUpper covers every state but Off`() {
        assertFalse(EffectiveShift.Off.isUpper)
        assertTrue(EffectiveShift.Auto.isUpper)
        assertTrue(EffectiveShift.Shift.isUpper)
        assertTrue(EffectiveShift.CapsLock.isUpper)
    }
}
