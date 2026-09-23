package dev.ybdn.ciao.clavier.domain.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordDeletionTest {

    @Test
    fun `deletes the last word`() {
        assertEquals("bonjour".length, wordDeletionLength("Salut bonjour"))
    }

    @Test
    fun `deletes trailing spaces together with the word`() {
        assertEquals("bonjour  ".length, wordDeletionLength("Salut bonjour  "))
    }

    @Test
    fun `keeps elisions and compound words whole`() {
        assertEquals("l'école".length, wordDeletionLength("Je vais à l'école"))
        assertEquals("aujourd’hui".length, wordDeletionLength("C'est aujourd’hui"))
        assertEquals("peut-être".length, wordDeletionLength("Oui peut-être"))
    }

    @Test
    fun `punctuation goes on its own`() {
        assertEquals(1, wordDeletionLength("Bonjour !"))
        assertEquals(2, wordDeletionLength("Bonjour ! "))
    }

    @Test
    fun `empty or blank text`() {
        assertEquals(0, wordDeletionLength(""))
        assertEquals(3, wordDeletionLength("   "))
    }

    @Test
    fun `backspace switches from characters to words after a while`() {
        assertFalse(BackspaceRepeat.deletesWord(0))
        assertFalse(BackspaceRepeat.deletesWord(BackspaceRepeat.CharRepeatsBeforeWords - 1))
        assertTrue(BackspaceRepeat.deletesWord(BackspaceRepeat.CharRepeatsBeforeWords))
        assertEquals(BackspaceRepeat.WordRepeatMs, BackspaceRepeat.delayAfter(BackspaceRepeat.CharRepeatsBeforeWords))
    }
}
