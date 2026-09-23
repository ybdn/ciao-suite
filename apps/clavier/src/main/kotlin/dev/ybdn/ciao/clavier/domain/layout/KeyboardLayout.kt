package dev.ybdn.ciao.clavier.domain.layout

/**
 * Dispositions AZERTY et symboles, reprises de la maquette validée (apps/clavier/docs/spec-v1.md
 * §6.1, §6.2). Chaque touche porte son poids (largeur relative dans sa rangée) et son fond :
 * les touches de fonction sont en `muted`, les touches de saisie en `surface`.
 */
object KeyboardLayout {

    data class Slot(val key: Key, val weight: Float = 1f, val muted: Boolean = false)

    private fun char(c: Char, muted: Boolean = false) =
        Slot(Key.Character(c, c.uppercaseChar()), muted = muted)

    private fun chars(row: String) = row.map { char(it) }

    /** Rangée 3 : une touche de fonction à gauche, sept caractères, le retour arrière à droite. */
    private fun thirdRow(leading: Slot, keys: String) =
        listOf(leading) + chars(keys) + Slot(Key.Backspace, weight = 1.5f, muted = true)

    /**
     * Rangée 4. La virgule et le point prennent le fond des touches de fonction ; les caractères
     * qui les remplacent sur les autres pages gardent le fond de saisie (cf. `bottomRow` de la
     * maquette).
     */
    private fun bottomRow(page: Key.Page, comma: Char, period: Char) = listOf(
        Slot(page, weight = 1.5f, muted = true),
        char(comma, muted = comma == ','),
        Slot(Key.Emoji, muted = true),
        Slot(Key.Space, weight = 4.5f),
        char(period, muted = period == '.'),
        Slot(Key.Enter, weight = 1.5f),
    )

    fun rows(page: KeyboardPage): List<List<Slot>> = when (page) {
        KeyboardPage.Letters -> listOf(
            chars("azertyuiop"),
            chars("qsdfghjklm"),
            thirdRow(Slot(Key.Shift, weight = 1.5f, muted = true), "wxcvbn'"),
            bottomRow(Key.Page("?123", KeyboardPage.Symbols1), ',', '.'),
        )

        KeyboardPage.Symbols1 -> listOf(
            chars("1234567890"),
            chars("@#€_&-+()/"),
            thirdRow(Slot(Key.Page("=\\<", KeyboardPage.Symbols2), weight = 1.5f, muted = true), "*\"':;!?"),
            bottomRow(Key.Page("ABC", KeyboardPage.Letters), ',', '.'),
        )

        KeyboardPage.Symbols2 -> listOf(
            chars("~`|•√π÷×§¶"),
            chars("£¥$¢^°={}\\"),
            thirdRow(Slot(Key.Page("?123", KeyboardPage.Symbols1), weight = 1.5f, muted = true), "%©®™✓[]"),
            bottomRow(Key.Page("ABC", KeyboardPage.Letters), '<', '>'),
        )
    }
}
