package dev.ybdn.ciao.clavier.domain.suggest

import kotlin.math.abs

/**
 * Coûts des écarts entre le mot tapé et un mot du dictionnaire (spec §7.1), en « fautes » : 1,0
 * pour une lettre en trop, en moins ou remplacée au hasard. Les écarts plausibles au clavier
 * coûtent moins : accent oublié, touche voisine, lettres inversées, apostrophe omise.
 */
internal object TypingCosts {
    /** Lettre sans accent tapée pour une lettre accentuée : `ecole` → `école`. */
    const val MissingAccent = 0.05f

    /** Mauvais accent : `é` tapé pour `è`. */
    const val WrongAccent = 0.3f

    /** Minuscule tapée pour une majuscule du dictionnaire : `paris` → `Paris`. */
    const val Case = 0.1f

    /** Touche voisine sur la disposition AZERTY. */
    const val Neighbour = 0.6f
    const val Substitution = 1f

    /** Deux lettres voisines inversées : `bonjuor` → `bonjour`. */
    const val Transposition = 0.7f

    /** Lettre du dictionnaire absente du mot tapé. */
    const val Omission = 1f

    /** Apostrophe ou trait d'union oubliés : `aujourdhui`, `peutetre`. */
    const val PunctuationOmission = 0.25f

    /** Apostrophe d'élision oubliée : `jai` → `j'ai`. Plus chère qu'une apostrophe dans un mot, le découpage étant incertain. */
    const val ElisionOmission = 0.5f

    /** Lettre tapée en trop. */
    const val Extra = 1f

    /** Lettre tapée deux fois : `bonnjour`. */
    const val DoubledExtra = 0.5f

    /** `oe` tapé pour `œ`, `ae` pour `æ`. */
    const val Ligature = 0.1f

    fun substitution(typed: Char, word: Char): Float {
        if (typed == word) return 0f
        val typedBase = fold(typed)
        if (typedBase == fold(word)) {
            val typedLower = typed.lowercaseChar()
            val wordLower = word.lowercaseChar()
            return when {
                typedLower == wordLower -> if (word.isUpperCase() && typed.isLowerCase()) Case else 0f
                typedLower == typedBase -> MissingAccent
                else -> WrongAccent
            }
        }
        return if (areNeighbours(typedBase, fold(word))) Neighbour else Substitution
    }

    /** Lettre doublée tapée une seule fois : `coment` → `comment`. */
    const val DoubledOmission = 0.4f

    fun omission(word: Char, doubled: Boolean): Float = when {
        word == '\'' || word == '-' -> PunctuationOmission
        doubled -> DoubledOmission
        else -> Omission
    }

    fun extra(typed: CharArray, index: Int): Float {
        val char = typed[index]
        return if (char == '\'' || char == '-' || (index > 0 && typed[index - 1] == char)) DoubledExtra else Extra
    }

    /** Minuscule sans accent ; `’` devient `'`. `œ` et `æ` restent (voir [Ligature]). */
    fun fold(char: Char): Char {
        val lower = char.lowercaseChar()
        val index = Accented.indexOf(lower)
        return when {
            index >= 0 -> Plain[index]
            lower == '’' -> '\''
            else -> lower
        }
    }

    private const val Accented = "àáâäãåçèéêëìíîïñòóôöõùúûüýÿ"
    private const val Plain = "aaaaaaceeeeiiiinooooouuuuyy"

    /** Colonne de chaque lettre sur la disposition AZERTY (§6.1) : la 3e rangée est décalée par ⇧. */
    private val KeyPositions: Map<Char, Pair<Float, Int>> = buildMap {
        "azertyuiop".forEachIndexed { x, c -> put(c, x.toFloat() to 0) }
        "qsdfghjklm".forEachIndexed { x, c -> put(c, x.toFloat() to 1) }
        "wxcvbn".forEachIndexed { x, c -> put(c, x + 1.5f to 2) }
    }

    fun areNeighbours(a: Char, b: Char): Boolean {
        val (ax, ay) = KeyPositions[a] ?: return false
        val (bx, by) = KeyPositions[b] ?: return false
        return when (abs(ay - by)) {
            0 -> abs(ax - bx) == 1f
            1 -> abs(ax - bx) <= 1f
            else -> false
        }
    }
}
