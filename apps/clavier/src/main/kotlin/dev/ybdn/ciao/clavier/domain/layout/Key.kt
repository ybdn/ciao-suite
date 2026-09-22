package dev.ybdn.ciao.clavier.domain.layout

/** Une touche de la page lettres (apps/clavier/docs/spec-v1.md §6.1). Kotlin pur, testable. */
sealed interface Key {
    data class Letter(val lower: Char, val upper: Char) : Key
    data object Apostrophe : Key
    data object Comma : Key
    data object Period : Key
    data object Space : Key
    data object Shift : Key
    data object Backspace : Key
    data object Enter : Key

    /** Bascule vers la page symboles (?123). Sans effet en v1 lot 2 : arrive au lot suivant. */
    data object Symbols : Key

    /** Ouvre le panneau emojis. Sans effet en v1 lot 2 : arrive au lot 6. */
    data object Emoji : Key
}
