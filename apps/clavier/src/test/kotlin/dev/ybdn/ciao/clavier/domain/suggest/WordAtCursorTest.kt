package dev.ybdn.ciao.clavier.domain.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordAtCursorTest {

    @Test
    fun `word being typed and the word before it`() {
        assertEquals(WordAtCursor("", "bonj", null), WordAtCursor.read("bonj", ""))
        assertEquals(WordAtCursor("", "nouv", "bonne"), WordAtCursor.read("Une bonne nouv", " "))
    }

    @Test
    fun `elision is set apart and becomes the context`() {
        assertEquals(WordAtCursor("l'", "ecole", "l'"), WordAtCursor.read("à l'ecole", ""))
        assertEquals(WordAtCursor("qu’", "il", "qu'"), WordAtCursor.read("Qu’il", "").let { it?.copy(elision = it.elision.lowercase()) })
    }

    @Test
    fun `nothing typed yet predicts from the previous word`() {
        assertEquals(WordAtCursor("", "", "je"), WordAtCursor.read("Alors je ", ""))
        assertEquals(WordAtCursor("", "", "année"), WordAtCursor.read("l'année ", ""))
        assertEquals(WordAtCursor("l'", "", "l'"), WordAtCursor.read("de l'", ""))
        assertEquals(WordAtCursor("", "", null), WordAtCursor.read("Fin. ", ""))
        assertEquals(WordAtCursor("", "", null), WordAtCursor.read("", ""))
    }

    @Test
    fun `no word in the middle of a word or next to digits`() {
        assertNull(WordAtCursor.read("bon", "jour"))
        assertNull(WordAtCursor.read("12h", ""))
        assertNull(WordAtCursor.read("aujourd'", ""))
    }

    @Test
    fun `suggestion takes the casing of the typed word`() {
        assertEquals("Bonjour", matchCasing("Bonj", "bonjour"))
        assertEquals("BONJOUR", matchCasing("BONJ", "bonjour"))
        assertEquals("bonjour", matchCasing("bonj", "bonjour"))
        assertEquals("Paris", matchCasing("paris", "Paris"))
        assertEquals("J'ai", matchCasing("Jai", "j'ai"))
        assertEquals("a", matchCasing("A", "a").lowercase())
    }
}
