package dev.ybdn.ciao.clavier.domain.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrenchTypographyTest {

    private val nbsp = FrenchTypography.NoBreakSpace

    @Test
    fun `double space after a word ends the sentence`() {
        assertEquals(TextEdit(1, ". "), FrenchTypography.doubleSpacePeriod("Bonjour "))
        assertEquals(TextEdit(1, ". "), FrenchTypography.doubleSpacePeriod("en 2026 "))
        assertEquals(TextEdit(1, ". "), FrenchTypography.doubleSpacePeriod("(voir plus haut) "))
        assertEquals(TextEdit(1, ". "), FrenchTypography.doubleSpacePeriod("« Oui » "))
    }

    @Test
    fun `double space after punctuation or spaces stays two spaces`() {
        assertNull(FrenchTypography.doubleSpacePeriod("Vraiment ! "))
        assertNull(FrenchTypography.doubleSpacePeriod("Fin. "))
        assertNull(FrenchTypography.doubleSpacePeriod("Et, "))
        assertNull(FrenchTypography.doubleSpacePeriod("  "))
        assertNull(FrenchTypography.doubleSpacePeriod(" "))
        assertNull(FrenchTypography.doubleSpacePeriod("Bonjour"))
    }

    @Test
    fun `the typed space before punctuation becomes non-breaking`() {
        assertEquals(TextEdit(1, "$nbsp!"), FrenchTypography.spaceBeforePunctuation('!', "Bonjour "))
        assertEquals(TextEdit(1, "$nbsp;"), FrenchTypography.spaceBeforePunctuation(';', "oui "))
    }

    @Test
    fun `a non-breaking space is added right after a word`() {
        assertEquals(TextEdit(0, "$nbsp?"), FrenchTypography.spaceBeforePunctuation('?', "Pourquoi"))
        assertEquals(TextEdit(0, "$nbsp:"), FrenchTypography.spaceBeforePunctuation(':', "« Voici »"))
    }

    @Test
    fun `no space in times, repeated punctuation, URLs or at the start`() {
        assertNull(FrenchTypography.spaceBeforePunctuation(':', "à 10"))
        assertNull(FrenchTypography.spaceBeforePunctuation('!', "Quoi ?"))
        assertNull(FrenchTypography.spaceBeforePunctuation('!', "Quoi$nbsp?"))
        assertNull(FrenchTypography.spaceBeforePunctuation(':', "voir https"))
        assertNull(FrenchTypography.spaceBeforePunctuation('?', ""))
        assertNull(FrenchTypography.spaceBeforePunctuation('?', "  "))
        assertNull(FrenchTypography.spaceBeforePunctuation('.', "Fin"))
    }

    @Test
    fun `elisions are split from the following word`() {
        assertEquals(Elision("l'", "ecole"), FrenchTypography.splitElision("l'ecole"))
        assertEquals(Elision("qu’", "il"), FrenchTypography.splitElision("qu’il"))
        assertEquals(Elision("Jusqu'", "ici"), FrenchTypography.splitElision("Jusqu'ici"))
        assertEquals(Elision("D'", "accord"), FrenchTypography.splitElision("D'accord"))
    }

    @Test
    fun `words that are not elisions stay whole`() {
        assertNull(FrenchTypography.splitElision("aujourd'hui"))
        assertNull(FrenchTypography.splitElision("l'"))
        assertNull(FrenchTypography.splitElision("'bonjour"))
        assertNull(FrenchTypography.splitElision("bonjour"))
    }
}
