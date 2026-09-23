package dev.ybdn.ciao.clavier.domain.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {

    private fun characters(slots: List<KeyboardLayout.Slot>) =
        slots.mapNotNull { (it.key as? Key.Character)?.lower }.joinToString("")

    @Test
    fun `each page has four rows`() {
        KeyboardPage.entries.forEach { page ->
            assertEquals(4, KeyboardLayout.rows(page).size)
        }
    }

    @Test
    fun `letters page follows the AZERTY layout`() {
        val rows = KeyboardLayout.rows(KeyboardPage.Letters)
        assertEquals("azertyuiop", characters(rows[0]))
        assertEquals("qsdfghjklm", characters(rows[1]))
        assertEquals("wxcvbn'", characters(rows[2]))
    }

    @Test
    fun `third row starts with a function key and ends with backspace`() {
        KeyboardPage.entries.forEach { page ->
            val thirdRow = KeyboardLayout.rows(page)[2]
            assertEquals(9, thirdRow.size)
            assertEquals(Key.Backspace, thirdRow.last().key)
            assertTrue(thirdRow.first().muted)
        }
    }

    @Test
    fun `pages link to each other`() {
        fun pageTargets(page: KeyboardPage) = KeyboardLayout.rows(page)
            .flatten()
            .mapNotNull { (it.key as? Key.Page)?.target }

        assertEquals(listOf(KeyboardPage.Symbols1), pageTargets(KeyboardPage.Letters))
        assertEquals(
            listOf(KeyboardPage.Symbols2, KeyboardPage.Letters),
            pageTargets(KeyboardPage.Symbols1),
        )
        assertEquals(
            listOf(KeyboardPage.Symbols1, KeyboardPage.Letters),
            pageTargets(KeyboardPage.Symbols2),
        )
    }

    @Test
    fun `comma and period take the function background, the symbols replacing them do not`() {
        val lettersBottom = KeyboardLayout.rows(KeyboardPage.Letters)[3]
        assertTrue(lettersBottom.single { it.key == Key.Character(',', ',') }.muted)
        assertTrue(lettersBottom.single { it.key == Key.Character('.', '.') }.muted)

        val symbols2Bottom = KeyboardLayout.rows(KeyboardPage.Symbols2)[3]
        assertFalse(symbols2Bottom.single { it.key == Key.Character('<', '<') }.muted)
        assertFalse(symbols2Bottom.single { it.key == Key.Character('>', '>') }.muted)
    }

    @Test
    fun `the space bar is the widest key`() {
        val bottomRow = KeyboardLayout.rows(KeyboardPage.Letters)[3]
        assertEquals(4.5f, bottomRow.single { it.key == Key.Space }.weight, 0f)
    }
}
