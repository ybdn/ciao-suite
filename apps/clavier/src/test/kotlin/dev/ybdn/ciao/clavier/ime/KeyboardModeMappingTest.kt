package dev.ybdn.ciao.clavier.ime

import android.text.InputType
import dev.ybdn.ciao.clavier.domain.layout.KeyboardMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardModeMappingTest {

    @Test
    fun `field types map to their keyboard`() {
        assertEquals(KeyboardMode.Text, keyboardModeFor(InputType.TYPE_CLASS_TEXT))
        assertEquals(
            KeyboardMode.Email,
            keyboardModeFor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
        )
        assertEquals(
            KeyboardMode.Email,
            keyboardModeFor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS),
        )
        assertEquals(KeyboardMode.Url, keyboardModeFor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI))
        assertEquals(
            KeyboardMode.Number,
            keyboardModeFor(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL),
        )
        assertEquals(
            KeyboardMode.Number,
            keyboardModeFor(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD),
        )
        assertEquals(KeyboardMode.DateTime, keyboardModeFor(InputType.TYPE_CLASS_DATETIME))
        assertEquals(KeyboardMode.Phone, keyboardModeFor(InputType.TYPE_CLASS_PHONE))
    }

    @Test
    fun `a password stays on letters, an unknown field too`() {
        assertEquals(
            KeyboardMode.Text,
            keyboardModeFor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
        )
        assertEquals(KeyboardMode.Text, keyboardModeFor(InputType.TYPE_NULL))
    }

    @Test
    fun `password fields are recognised`() {
        assertTrue(isPasswordField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertTrue(isPasswordField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertTrue(isPasswordField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertTrue(isPasswordField(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertFalse(isPasswordField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        assertFalse(isPasswordField(InputType.TYPE_CLASS_PHONE))
    }
}
