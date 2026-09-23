package dev.ybdn.ciao.clavier.domain.layout

/** Pages de touches de la v1 (apps/clavier/docs/spec-v1.md §6.1, §6.2). */
enum class KeyboardPage { Letters, Symbols1, Symbols2 }

/** Une touche du clavier. Kotlin pur, testable. */
sealed interface Key {
    /** Caractère inséré tel quel ; [upper] est utilisé quand la majuscule est active. */
    data class Character(val lower: Char, val upper: Char) : Key

    data object Space : Key
    data object Shift : Key
    data object Backspace : Key
    data object Enter : Key

    /** Ouvre le panneau emojis (lot 6). */
    data object Emoji : Key

    /** Bascule vers une autre page de touches (« ?123 », « =\< », « ABC »). */
    data class Page(val label: String, val target: KeyboardPage) : Key
}
