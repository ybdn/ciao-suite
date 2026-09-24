package dev.ybdn.ciao.clavier.domain.input

/**
 * Nombre de caractères à effacer pour supprimer le mot avant le curseur (accélération du retour
 * arrière maintenu, apps/clavier/docs/spec-v1.md §6.1) : les espaces qui suivent le mot, puis le
 * mot lui-même (lettres, chiffres, traits d'union et apostrophes internes). Si le curseur suit une
 * ponctuation, seule celle-ci part. 0 si le texte est vide.
 */
fun wordDeletionLength(textBeforeCursor: CharSequence): Int {
    var end = textBeforeCursor.length
    while (end > 0 && textBeforeCursor[end - 1].isWhitespace()) end--
    if (end == 0) return textBeforeCursor.length
    if (!textBeforeCursor[end - 1].isWordPart()) return textBeforeCursor.length - end + 1
    var start = end
    while (start > 0 && textBeforeCursor[start - 1].isWordPart()) start--
    return textBeforeCursor.length - start
}

private fun Char.isWordPart() = isLetterOrDigit() || this == '-' || this == '\'' || this == '’'

/**
 * Cadence du retour arrière maintenu : après [InitialDelayMs], un caractère toutes les
 * [CharRepeatMs] ; au-delà de [CharRepeatsBeforeWords] répétitions, un mot toutes les
 * [WordRepeatMs].
 */
object BackspaceRepeat {
    const val InitialDelayMs = 400L
    const val CharRepeatMs = 60L
    const val CharRepeatsBeforeWords = 15
    const val WordRepeatMs = 200L

    fun deletesWord(repeatIndex: Int): Boolean = repeatIndex >= CharRepeatsBeforeWords

    fun delayAfter(repeatIndex: Int): Long = if (deletesWord(repeatIndex)) WordRepeatMs else CharRepeatMs
}
