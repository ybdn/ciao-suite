package dev.ybdn.ciao.clavier.domain.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftStateTest {

    @Test
    fun `simple tap from Off goes to Shift`() {
        assertEquals(ShiftState.Shift, ShiftState.Off.onShiftTap(isDoubleTap = false))
    }

    @Test
    fun `double tap from Off goes to CapsLock`() {
        assertEquals(ShiftState.CapsLock, ShiftState.Off.onShiftTap(isDoubleTap = true))
    }

    @Test
    fun `tap from Shift goes back to Off`() {
        assertEquals(ShiftState.Off, ShiftState.Shift.onShiftTap(isDoubleTap = false))
    }

    @Test
    fun `tap from CapsLock unlocks to Off`() {
        assertEquals(ShiftState.Off, ShiftState.CapsLock.onShiftTap(isDoubleTap = false))
        assertEquals(ShiftState.Off, ShiftState.CapsLock.onShiftTap(isDoubleTap = true))
    }

    @Test
    fun `Shift falls back to Off after a letter is typed`() {
        assertEquals(ShiftState.Off, ShiftState.Shift.afterLetterTyped())
    }

    @Test
    fun `CapsLock and Off are unchanged after a letter is typed`() {
        assertEquals(ShiftState.CapsLock, ShiftState.CapsLock.afterLetterTyped())
        assertEquals(ShiftState.Off, ShiftState.Off.afterLetterTyped())
    }

    @Test
    fun `isUpper reflects Shift and CapsLock only`() {
        assertFalse(ShiftState.Off.isUpper)
        assertTrue(ShiftState.Shift.isUpper)
        assertTrue(ShiftState.CapsLock.isUpper)
    }
}
