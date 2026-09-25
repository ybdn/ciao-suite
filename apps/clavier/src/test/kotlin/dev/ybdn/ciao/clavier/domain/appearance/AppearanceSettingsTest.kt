package dev.ybdn.ciao.clavier.domain.appearance

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceSettingsTest {

    @Test
    fun `system mode follows the system theme, light and dark are fixed`() {
        assertEquals(true, ThemeMode.System.resolveDarkTheme(systemDark = true))
        assertEquals(false, ThemeMode.System.resolveDarkTheme(systemDark = false))
        assertEquals(false, ThemeMode.Light.resolveDarkTheme(systemDark = true))
        assertEquals(true, ThemeMode.Dark.resolveDarkTheme(systemDark = false))
    }

    @Test
    fun `standard keyboard height keeps the dimensions calibrated on Gboard`() {
        assertEquals(1f, KeyboardHeight.Standard.scale)
        assertEquals(true, KeyboardHeight.Compact.scale < KeyboardHeight.Standard.scale)
        assertEquals(true, KeyboardHeight.Tall.scale > KeyboardHeight.Standard.scale)
    }
}
