package dev.ybdn.ciao.clavier.domain.layout

/**
 * Variantes proposées par appui long (apps/clavier/docs/spec-v1.md §6.1, §6.2), dans l'ordre
 * d'affichage : la première est présélectionnée. Les variantes des lettres sont en minuscules ;
 * [forCase] les passe en majuscules quand la majuscule est active.
 */
object KeyVariants {

    private val byCharacter: Map<Char, List<String>> = mapOf(
        'e' to listOf("é", "è", "ê", "ë", "€"),
        'a' to listOf("à", "â", "æ", "ä"),
        'c' to listOf("ç"),
        'u' to listOf("ù", "û", "ü"),
        'i' to listOf("î", "ï"),
        'o' to listOf("ô", "œ", "ö"),
        'y' to listOf("ÿ"),
        'n' to listOf("ñ"),
        '\'' to listOf("’", "«", "»"),
        '.' to listOf("…", "!", "?"),
        ',' to listOf(";", ":"),
    )

    /** Domaines proposés par appui long sur la touche « .fr » du clavier URL. */
    val domains: List<String> = listOf(".com", ".org", ".net", ".eu")

    fun of(character: Char): List<String> = byCharacter[character].orEmpty()

    fun forCase(variants: List<String>, upper: Boolean): List<String> =
        if (upper) variants.map { it.uppercase() } else variants
}
