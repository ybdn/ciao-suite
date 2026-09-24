package dev.ybdn.ciao.clavier.domain.suggest

import dev.ybdn.ciao.clavier.domain.input.FrenchTypography

/**
 * Mot en cours de frappe, juste avant le curseur : [elision] (`l'`, ou vide) puis [word], le mot
 * cherché seul (§6.3). [previousWord] est le mot qui le précède, pour le contexte (bigrammes) : le
 * mot avant l'espace, ou l'élision elle-même (`l'` → `école`).
 */
data class WordAtCursor(val elision: String, val word: String, val previousWord: String?) {
    /** Longueur à remplacer dans le champ pour appliquer une suggestion. */
    val length: Int get() = elision.length + word.length

    companion object {
        /**
         * Lit le mot avant le curseur dans [before] ; null si le curseur est au milieu d'un mot
         * ([after] commence par une lettre) ou colle à des chiffres.
         */
        fun read(before: CharSequence, after: CharSequence): WordAtCursor? {
            if (after.firstOrNull()?.isWordChar() == true) return null
            val token = before.takeLastWhile { it.isWordChar() }.trimStart('\'', '’', '-').toString()
            val beforeToken = before.subSequence(0, before.length - token.length)
            if (beforeToken.lastOrNull()?.isLetterOrDigit() == true) return null

            if (token.isEmpty()) return WordAtCursor("", "", previousWord(beforeToken))
            // « l' » seul : l'élision est tapée, le mot suivant pas encore.
            if (token.last() == '\'' || token.last() == '’') {
                if (!FrenchTypography.isElidedPrefix(token.dropLast(1))) return null
                return WordAtCursor(token, "", normalize(token.lowercase()))
            }
            val elision = FrenchTypography.splitElision(token)
            return if (elision != null) {
                WordAtCursor(elision.prefix, elision.word, normalize(elision.prefix.lowercase()))
            } else {
                WordAtCursor("", token, previousWord(beforeToken))
            }
        }

        /** Le mot juste avant une espace unique en fin de [text] (dernier mot de `l'année `). */
        private fun previousWord(text: CharSequence): String? {
            if (text.length < 2 || text.last() != ' ') return null
            val beforeSpace = text.subSequence(0, text.length - 1)
            val token = beforeSpace.takeLastWhile { it.isWordChar() }.trimStart('\'', '’', '-').toString()
            if (token.isEmpty() || !token.last().isLetter()) return null
            if (beforeSpace.getOrNull(beforeSpace.length - token.length - 1)?.isDigit() == true) return null
            return normalize(FrenchTypography.splitElision(token)?.word ?: token)
        }

        private fun normalize(word: String) = word.replace('’', '\'')

        private fun Char.isWordChar() = isLetter() || this == '\'' || this == '’' || this == '-'
    }
}

/**
 * Casse d'une suggestion selon celle du mot tapé : `Bonj` → `Bonjour`, `ECOL` → `ÉCOLE`. Un nom
 * propre ou un sigle du dictionnaire garde sa casse (`paris` → `Paris`).
 */
fun matchCasing(typed: String, word: String): String = when {
    typed.length >= 2 && typed.all { !it.isLetter() || it.isUpperCase() } && typed.any { it.isLetter() } -> word.uppercase()
    word.any { it.isUpperCase() } -> word
    typed.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercaseChar() }
    else -> word
}
