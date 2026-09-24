package dev.ybdn.ciao.clavier.domain.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalDictionaryTest {

    private val dictionary = SuggestionEngineTest.dictionary
    private val base = SuggestionEngine(dictionary)

    private fun learned(word: String, uses: Int = 2) = PersonalWord(word, uses, learned = true, lastUsedAt = 0)

    private fun engineWith(vararg words: PersonalWord) = base.withPersonal(PersonalLexicon.build(dictionary, words.toList()))

    @Test
    fun `an unknown word is learned the second time it is kept`() {
        val once = PersonalDictionaryRules.kept(null, "tkt", now = 1)
        assertFalse(once.learned)
        val twice = PersonalDictionaryRules.kept(once, "tkt", now = 2)
        assertTrue(twice.learned)
        assertEquals(2, twice.uses)
        assertEquals(2, twice.lastUsedAt)
    }

    @Test
    fun `the least capitalized form wins`() {
        assertEquals("mdr", PersonalDictionaryRules.kept(PersonalWord("Mdr", 1, false, 0), "mdr", 1).word)
        assertEquals("mdr", PersonalDictionaryRules.kept(PersonalWord("mdr", 1, false, 0), "Mdr", 1).word)
        assertEquals("Kévin", PersonalDictionaryRules.kept(PersonalWord("Kévin", 1, false, 0), "Kévin", 1).word)
    }

    @Test
    fun `only real words are learnable`() {
        assertTrue(PersonalDictionaryRules.isLearnable("tkt"))
        assertTrue(PersonalDictionaryRules.isLearnable("aujourd'hui"))
        assertTrue(PersonalDictionaryRules.isLearnable("peut-être"))
        assertFalse(PersonalDictionaryRules.isLearnable("a"))
        assertFalse(PersonalDictionaryRules.isLearnable("l'"))
        assertFalse(PersonalDictionaryRules.isLearnable("abc123"))
        assertFalse(PersonalDictionaryRules.isLearnable("deux mots"))
        assertFalse(PersonalDictionaryRules.isLearnable("x".repeat(PersonalDictionaryRules.MaxWordLength + 1)))
    }

    @Test
    fun `manual entry is trimmed and validated`() {
        assertEquals("aujourd'hui", PersonalDictionaryRules.normalizeManual("  aujourd’hui "))
        assertNull(PersonalDictionaryRules.normalizeManual("deux mots"))
        assertNull(PersonalDictionaryRules.normalizeManual(""))
    }

    @Test
    fun `oldest candidates are forgotten beyond the limit`() {
        val candidates = (0 until PersonalDictionaryRules.MaxCandidates + 2).map { PersonalWord("mot$it", 1, false, lastUsedAt = it.toLong()) }
        val learned = PersonalWord("ancien", 5, true, lastUsedAt = -1)
        val forgotten = PersonalDictionaryRules.candidatesToForget(candidates + learned)
        assertEquals(listOf("mot1", "mot0"), forgotten.map { it.word })
    }

    @Test
    fun `search ignores case and accents`() {
        assertTrue(PersonalDictionaryRules.matches("École", "ecol"))
        assertTrue(PersonalDictionaryRules.matches("tkt", " "))
        assertFalse(PersonalDictionaryRules.matches("tkt", "mdr"))
    }

    @Test
    fun `a learned word is suggested and never corrected`() {
        assertEquals("tôt", base.suggest("tkt").autocorrection)
        val engine = engineWith(learned("tkt"))
        val result = engine.suggest("tkt")
        assertEquals("tkt", result.words.first())
        assertNull(result.autocorrection)
        assertTrue(engine.isKnown("Tkt"))
        assertFalse(engine.isInDictionary("tkt"))
    }

    @Test
    fun `a learned word is completed and found despite a typo`() {
        val engine = engineWith(learned("chocapics"))
        assertTrue("chocapics" in engine.suggest("chocap").words)
        assertTrue("chocapics" in engine.suggest("chocapivs").words)
    }

    @Test
    fun `candidates seen once are not suggested`() {
        val engine = engineWith(PersonalWord("tkt", 1, learned = false, lastUsedAt = 0))
        assertEquals("tôt", engine.suggest("tkt").autocorrection)
    }

    @Test
    fun `words often chosen in the bar move up`() {
        assertEquals(listOf("nouveau", "nouvelle", "nouveaux"), base.suggest("nouv").words)
        val engine = engineWith(PersonalWord("Nouveaux", 8, learned = false, lastUsedAt = 0))
        val words = engine.suggest("nouv").words
        assertTrue(words.toString(), words.indexOf("nouveaux") < 2)
    }
}
