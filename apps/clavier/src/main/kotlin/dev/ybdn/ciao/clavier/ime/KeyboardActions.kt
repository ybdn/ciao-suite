package dev.ybdn.ciao.clavier.ime

/** Ce que l'interface du clavier demande au champ actif, via le service (`InputConnection`). */
interface KeyboardActions {
    fun commitText(text: String)

    /** Efface la sélection s'il y en a une, sinon le caractère avant le curseur. */
    fun deleteBackward()

    /** Efface le mot avant le curseur (retour arrière maintenu longtemps). */
    fun deleteWordBackward()

    /** Déplace le curseur de [steps] caractères (négatif vers la gauche). */
    fun moveCursor(steps: Int)

    /** Touche Entrée : action du champ ou retour à la ligne. */
    fun enter()

    /** Insère un emoji et, hors navigation privée, le range dans les récents. */
    fun emojiTyped(emoji: String)

    /** Retient la couleur de peau choisie pour [base] (hors navigation privée). */
    fun skinToneChosen(base: String, variant: String)

    /** Colle un texte de l'historique ou de la puce « Coller ». */
    fun pasteClip(text: String)

    fun setClipPinned(id: Long, pinned: Boolean)

    fun deleteClip(id: Long)

    /** Masque la puce « Coller » jusqu'à la prochaine copie. */
    fun dismissPasteChip()

    /** Applique une proposition de la barre de suggestions au mot en cours. */
    fun pickSuggestion(item: SuggestionItem)

    /** Retour haptique d'une frappe. */
    fun keyFeedback()
}
