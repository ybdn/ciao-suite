package dev.ybdn.ciao.clavier.ime

import android.view.inputmethod.EditorInfo

/**
 * Icône/action de la touche Entrée : suit `imeOptions` du champ actif (apps/clavier/docs/spec-v1.md
 * §6.1). Un vrai jeu d'icônes (Material) arrivera si besoin ; des glyphes suffisent en v1.
 */
enum class EnterKeyLabel(val symbol: String) {
    Send("➤"),
    Search("🔍"),
    Next("⇥"),
    Done("✓"),
    Go("➜"),
    Newline("⏎"),
}

fun EditorInfo?.enterKeyLabel(): EnterKeyLabel {
    val action = (this?.imeOptions ?: EditorInfo.IME_ACTION_UNSPECIFIED) and EditorInfo.IME_MASK_ACTION
    return when (action) {
        EditorInfo.IME_ACTION_SEND -> EnterKeyLabel.Send
        EditorInfo.IME_ACTION_SEARCH -> EnterKeyLabel.Search
        EditorInfo.IME_ACTION_NEXT -> EnterKeyLabel.Next
        EditorInfo.IME_ACTION_DONE -> EnterKeyLabel.Done
        EditorInfo.IME_ACTION_GO -> EnterKeyLabel.Go
        else -> EnterKeyLabel.Newline
    }
}
