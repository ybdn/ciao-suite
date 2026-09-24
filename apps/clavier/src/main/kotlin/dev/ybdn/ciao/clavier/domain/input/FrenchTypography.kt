package dev.ybdn.ciao.clavier.domain.input

/** Modification du texte avant le curseur : effacer [deleteBefore] caractères, puis insérer [insert]. */
data class TextEdit(val deleteBefore: Int, val insert: String)

/** Mot précédé d'une élision (`l'`, `qu’`…) : [prefix] garde l'apostrophe tapée. */
data class Elision(val prefix: String, val word: String)

/**
 * Règles typographiques françaises de la frappe (apps/clavier/docs/spec-v1.md §6.3). Kotlin pur :
 * chaque règle reçoit le texte avant le curseur et renvoie la modification à faire, ou null pour
 * insérer le caractère tapé tel quel.
 */
object FrenchTypography {

    /** Espace insécable (U+00A0), mieux affichée par les apps que l'espace fine (U+202F). */
    const val NoBreakSpace = ' '

    /** Ponctuation précédée d'une espace insécable en français. */
    private const val SpacedPunctuation = ";:!?"

    /** Après ces caractères, un point peut clore la phrase : lettres, chiffres, fermetures. */
    private fun Char.endsSentenceWord() = isLetterOrDigit() || this in ")]»\"'’"

    /**
     * Double espace → point (§6.3) : appelée quand l'utilisateur tape une espace juste après une
     * autre. Si l'espace précédente suit un mot, elle devient « . ». Pas après une ponctuation
     * (« … », « ! ») ni une autre espace.
     */
    fun doubleSpacePeriod(textBeforeCursor: CharSequence): TextEdit? {
        val length = textBeforeCursor.length
        if (length < 2 || textBeforeCursor[length - 1] != ' ') return null
        if (!textBeforeCursor[length - 2].endsSentenceWord()) return null
        return TextEdit(deleteBefore = 1, insert = ". ")
    }

    /**
     * Espace insécable avant `; : ! ?` (§6.3, réglage désactivé par défaut) : l'espace tapée avant
     * la ponctuation devient insécable, ou une espace insécable est ajoutée après un mot. Rien
     * après un chiffre (« 10:30 »), une autre ponctuation (« ?! ») ou un schéma d'URL (« https: »).
     */
    fun spaceBeforePunctuation(punctuation: Char, textBeforeCursor: CharSequence): TextEdit? {
        if (punctuation !in SpacedPunctuation || textBeforeCursor.isEmpty()) return null
        val last = textBeforeCursor.last()
        val insert = "$NoBreakSpace$punctuation"
        return when {
            last == ' ' -> {
                val beforeSpace = textBeforeCursor.getOrNull(textBeforeCursor.length - 2)
                if (beforeSpace == null || beforeSpace.isWhitespace()) null else TextEdit(1, insert)
            }
            last.isLetter() || last in ")]»\"" -> {
                if (punctuation == ':' && lastWord(textBeforeCursor).lowercase() in UrlSchemes) null else TextEdit(0, insert)
            }
            else -> null
        }
    }

    private val UrlSchemes = setOf("http", "https", "ftp", "mailto", "tel")

    private fun lastWord(text: CharSequence): String = text.takeLastWhile { it.isLetter() }.toString()

    /** Élisions du français : le mot qui suit est cherché seul par les suggestions (§6.3, §7). */
    val ElidedPrefixes = listOf("jusqu", "lorsqu", "puisqu", "quoiqu", "qu", "l", "d", "j", "m", "n", "s", "t", "c")

    /** [prefix] (sans apostrophe) s'élide : `l`, `qu`, `jusqu`… */
    fun isElidedPrefix(prefix: String): Boolean = prefix.lowercase() in ElidedPrefixes

    /** `l'ecole` → `l'` + `ecole` ; null si [word] ne commence pas par une élision. */
    fun splitElision(word: String): Elision? {
        val apostrophe = word.indexOfFirst { it == '\'' || it == '’' }
        if (apostrophe <= 0 || apostrophe == word.lastIndex) return null
        val prefix = word.substring(0, apostrophe)
        if (!isElidedPrefix(prefix)) return null
        return Elision(prefix = word.substring(0, apostrophe + 1), word = word.substring(apostrophe + 1))
    }
}
