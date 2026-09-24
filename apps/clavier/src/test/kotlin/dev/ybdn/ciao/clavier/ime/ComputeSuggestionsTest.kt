package dev.ybdn.ciao.clavier.ime

import dev.ybdn.ciao.clavier.domain.suggest.SuggestionEngine
import dev.ybdn.ciao.clavier.domain.suggest.SuggestionEngineTest
import dev.ybdn.ciao.clavier.domain.suggest.WordAtCursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeSuggestionsTest {

    private val engine = SuggestionEngine(SuggestionEngineTest.dictionary)

    private fun compute(word: String, autocorrect: Boolean = true, rejected: Set<String> = emptySet()) =
        computeSuggestions(engine, WordAtCursor("", word, null), autocorrect, rejected)

    @Test
    fun `autocorrection goes in the middle, the typed word on the left`() {
        val computed = compute("bonjoir")
        assertEquals("bonjour", computed.autocorrection)
        assertEquals(SuggestionItem("bonjoir", SuggestionItem.Kind.Typed), computed.bar.slots[0])
        assertEquals(SuggestionItem("bonjour", SuggestionItem.Kind.Autocorrection), computed.bar.slots[1])
        assertTrue(computed.bar.forTypedWord)
    }

    @Test
    fun `best suggestion in the middle without autocorrection`() {
        val computed = compute("bonjoir", autocorrect = false)
        assertNull(computed.autocorrection)
        assertEquals(SuggestionItem("bonjour", SuggestionItem.Kind.Word), computed.bar.slots[1])
    }

    @Test
    fun `a rejected word is no longer corrected`() {
        assertNull(compute("bonjoir", rejected = setOf("bonjoir")).autocorrection)
    }

    @Test
    fun `casing follows the typed word`() {
        assertEquals("Bonjour", compute("Bonjoir").autocorrection)
        assertEquals("J'ai", compute("Jai").autocorrection)
    }

    @Test
    fun `predictions after a word, nothing after punctuation`() {
        val after = computeSuggestions(engine, WordAtCursor("", "", "je"), autocorrect = true, rejected = emptySet())
        assertTrue(after.bar.slots.any { it?.text == "suis" && it.kind == SuggestionItem.Kind.Prediction })
        assertTrue(!after.bar.forTypedWord)
        val none = computeSuggestions(engine, WordAtCursor("", "", null), autocorrect = true, rejected = emptySet())
        assertTrue(none.bar.slots.isEmpty())
    }
}
