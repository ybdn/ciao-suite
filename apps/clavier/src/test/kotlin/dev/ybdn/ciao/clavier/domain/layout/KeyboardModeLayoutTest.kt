package dev.ybdn.ciao.clavier.domain.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardModeLayoutTest {

    private fun lettersBottomRow(mode: KeyboardMode) = KeyboardLayout.rows(KeyboardPage.Letters, mode)[3]

    private fun labels(rows: List<List<KeyboardLayout.Slot>>) = rows.map { row ->
        row.joinToString(" ") { slot ->
            when (val key = slot.key) {
                is Key.Character -> key.lower.toString()
                Key.Space -> "␣"
                Key.Backspace -> "⌫"
                Key.Enter -> "⏎"
                else -> "?"
            }
        }
    }

    @Test
    fun `email replaces the comma with an at sign`() {
        val row = lettersBottomRow(KeyboardMode.Email)
        assertEquals(Key.Character('@', '@'), row[1].key)
        assertEquals(Key.Emoji, row[2].key)
    }

    @Test
    fun `url replaces the comma with a slash and the emoji key with a domain key`() {
        val row = lettersBottomRow(KeyboardMode.Url)
        assertEquals(Key.Character('/', '/'), row[1].key)
        assertEquals(Key.Text(".fr"), row[2].key)
        assertEquals(listOf(".com", ".org", ".net", ".eu"), row[2].variants)
    }

    @Test
    fun `symbol pages keep the comma in every text mode`() {
        listOf(KeyboardMode.Email, KeyboardMode.Url).forEach { mode ->
            val row = KeyboardLayout.rows(KeyboardPage.Symbols1, mode)[3]
            assertEquals(Key.Character(',', ','), row[1].key)
        }
    }

    @Test
    fun `number pad`() {
        assertEquals(
            listOf("1 2 3 -", "4 5 6 ␣", "7 8 9 ⌫", ", 0 . ⏎"),
            labels(KeyboardLayout.rows(KeyboardPage.Letters, KeyboardMode.Number)),
        )
    }

    @Test
    fun `date pad`() {
        assertEquals(
            listOf("1 2 3 -", "4 5 6 ␣", "7 8 9 ⌫", "/ 0 : ⏎"),
            labels(KeyboardLayout.rows(KeyboardPage.Letters, KeyboardMode.DateTime)),
        )
    }

    @Test
    fun `phone pad offers pause and wait by long press`() {
        val rows = KeyboardLayout.rows(KeyboardPage.Letters, KeyboardMode.Phone)
        assertEquals(listOf("1 2 3 +", "4 5 6 ␣", "7 8 9 ⌫", "* 0 # ⏎"), labels(rows))
        val bottom = rows[3]
        assertEquals(listOf(","), bottom[0].variants)
        assertEquals(listOf("+"), bottom[1].variants)
        assertEquals(listOf(";"), bottom[2].variants)
    }

    @Test
    fun `pads ignore the page and offer no punctuation by long press`() {
        val number = KeyboardLayout.rows(KeyboardPage.Symbols2, KeyboardMode.Number)
        assertEquals(KeyboardLayout.rows(KeyboardPage.Letters, KeyboardMode.Number), number)
        assertTrue(number[3][2].variants.isEmpty())
        assertFalse(number.flatten().any { it.key is Key.Page })
    }
}
