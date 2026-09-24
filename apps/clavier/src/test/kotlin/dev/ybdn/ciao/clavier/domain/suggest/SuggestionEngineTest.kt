package dev.ybdn.ciao.clavier.domain.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Moteur de suggestions sur le vrai dictionnaire compilé (chemin passé par Gradle, voir
 * apps/clavier/build.gradle.kts) et le jeu de cas français versionné `suggest/cas-francais.tsv`.
 */
class SuggestionEngineTest {

    private val engine = SuggestionEngine(dictionary)

    @Test
    fun `versioned French cases`() {
        val failures = cases().mapNotNull { case ->
            val result = engine.suggest(case.typed, case.previousWord)
            val top = result.words.firstOrNull()
            if (top == case.expectedTop && result.autocorrection == case.expectedAutocorrection) {
                null
            } else {
                "${case.typed} : attendu ${case.expectedTop} / ${case.expectedAutocorrection}, " +
                    "obtenu ${result.words} / ${result.autocorrection}"
            }
        }
        assertTrue(failures.joinToString("\n", prefix = "\n"), failures.isEmpty())
    }

    @Test
    fun `at most three distinct words`() {
        val words = engine.suggest("bon").words
        assertEquals(3, words.size)
        assertEquals(words.distinct(), words)
    }

    @Test
    fun `previous word breaks ties`() {
        assertTrue(engine.suggest("nouv", previousWord = "bonne").words.first() == "nouvelle")
    }

    @Test
    fun `typed oe finds the ligature`() {
        assertTrue("vœu" in engine.suggest("voeu").words)
        assertTrue("œuvre" in engine.suggest("oeuvre").words)
    }

    @Test
    fun `next word prediction`() {
        assertTrue("suis" in engine.predict("je"))
        assertTrue(engine.predict("Je").isNotEmpty())
        assertTrue(engine.predict("xqzw").isEmpty())
    }

    @Test
    fun `unknown word far from everything is not corrected`() {
        val result = engine.suggest("xqzwk")
        assertEquals(null, result.autocorrection)
    }

    @Test
    fun `under 50 ms per keystroke`() {
        val words = listOf("b", "bo", "bon", "bonj", "bonjo", "bonjoi", "bonjoir", "maintenznt", "anticonstitutionnel", "e", "a")
        repeat(3) { words.forEach { engine.suggest(it, "je") } } // Échauffement du JIT.
        val start = System.nanoTime()
        words.forEach { engine.suggest(it, "je") }
        val averageMs = (System.nanoTime() - start) / 1_000_000.0 / words.size
        assertTrue("$averageMs ms en moyenne", averageMs < 50)
    }

    private data class Case(val typed: String, val previousWord: String?, val expectedTop: String, val expectedAutocorrection: String?)

    private fun cases(): List<Case> {
        val stream = javaClass.classLoader!!.getResourceAsStream("suggest/cas-francais.tsv")!!
        return stream.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val (typed, previous, top, autocorrection) = line.split('\t')
                Case(typed, previous.takeIf { it != "-" }, top, autocorrection.takeIf { it != "-" })
            }
    }

    companion object {
        val dictionary: Dictionary by lazy {
            val path = System.getProperty("clavier.dictionary") ?: error("Lancer les tests par Gradle (propriété clavier.dictionary)")
            File(path).inputStream().use(Dictionary::read)
        }
    }
}
