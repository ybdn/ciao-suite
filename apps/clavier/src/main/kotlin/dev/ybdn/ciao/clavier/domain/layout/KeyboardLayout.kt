package dev.ybdn.ciao.clavier.domain.layout

/**
 * Dispositions AZERTY, symboles et pavés, reprises de la maquette validée
 * (apps/clavier/docs/spec-v1.md §6.1, §6.2). Chaque touche porte son poids (largeur relative dans
 * sa rangée), son fond (les touches de fonction sont en `muted`, les touches de saisie en
 * `surface`) et ses variantes par appui long.
 */
object KeyboardLayout {

    data class Slot(
        val key: Key,
        val weight: Float = 1f,
        val muted: Boolean = false,
        val variants: List<String> = emptyList(),
    )

    private fun char(c: Char, muted: Boolean = false, variants: List<String> = KeyVariants.of(c)) =
        Slot(Key.Character(c, c.uppercaseChar()), muted = muted, variants = variants)

    private fun chars(row: String) = row.map { char(it) }

    /** Rangée 3 : une touche de fonction à gauche, sept caractères, le retour arrière à droite. */
    private fun thirdRow(leading: Slot, keys: String) =
        listOf(leading) + chars(keys) + Slot(Key.Backspace, weight = 1.5f, muted = true)

    /**
     * Rangée 4. La virgule et le point prennent le fond des touches de fonction ; les caractères
     * qui les remplacent sur les autres pages gardent le fond de saisie (cf. `bottomRow` de la
     * maquette). Sur la page lettres, l'e-mail remplace la virgule par « @ », l'URL par « / » et
     * l'emoji par « .fr » (§6.2).
     */
    private fun bottomRow(page: Key.Page, comma: Slot, period: Char, emoji: Slot = Slot(Key.Emoji, muted = true)) =
        listOf(
            Slot(page, weight = 1.5f, muted = true),
            comma,
            emoji,
            Slot(Key.Space, weight = 4.5f),
            char(period, muted = period == '.'),
            Slot(Key.Enter, weight = 1.5f),
        )

    private fun lettersBottomRow(mode: KeyboardMode): List<Slot> {
        val symbols = Key.Page("?123", KeyboardPage.Symbols1)
        return when (mode) {
            KeyboardMode.Email -> bottomRow(symbols, char('@', muted = true), '.')
            KeyboardMode.Url -> bottomRow(
                symbols,
                comma = char('/', muted = true),
                period = '.',
                emoji = Slot(Key.Text(".fr"), muted = true, variants = KeyVariants.domains),
            )
            else -> bottomRow(symbols, char(',', muted = true), '.')
        }
    }

    /** Touche de pavé : pas d'accents ni de ponctuation par appui long, sauf [variants]. */
    private fun padChar(c: Char, muted: Boolean = false, variants: List<String> = emptyList()) =
        char(c, muted, variants)

    /**
     * Pavé de quatre colonnes (numérique, date, téléphone) : trois colonnes de chiffres et une
     * colonne de fonctions, sur quatre rangées pour garder la hauteur du clavier.
     */
    private fun pad(topRight: Slot, bottomLeft: Slot, bottomRight: Slot, zeroVariants: List<String> = emptyList()) = listOf(
        chars("123") + topRight,
        chars("456") + Slot(Key.Space, muted = true),
        chars("789") + Slot(Key.Backspace, muted = true),
        listOf(bottomLeft, padChar('0', variants = zeroVariants), bottomRight, Slot(Key.Enter)),
    )

    fun rows(page: KeyboardPage, mode: KeyboardMode = KeyboardMode.Text): List<List<Slot>> = when (mode) {
        KeyboardMode.Number -> pad(padChar('-', muted = true), padChar(','), padChar('.'))
        KeyboardMode.DateTime -> pad(padChar('-', muted = true), padChar('/'), padChar(':'))
        KeyboardMode.Phone -> pad(
            padChar('+', muted = true),
            padChar('*', variants = listOf(",")),
            padChar('#', variants = listOf(";")),
            zeroVariants = listOf("+"),
        )
        KeyboardMode.Text, KeyboardMode.Email, KeyboardMode.Url -> textRows(page, mode)
    }

    private fun textRows(page: KeyboardPage, mode: KeyboardMode): List<List<Slot>> = when (page) {
        KeyboardPage.Letters -> listOf(
            chars("azertyuiop"),
            chars("qsdfghjklm"),
            thirdRow(Slot(Key.Shift, weight = 1.5f, muted = true), "wxcvbn'"),
            lettersBottomRow(mode),
        )

        KeyboardPage.Symbols1 -> listOf(
            chars("1234567890"),
            chars("@#€_&-+()/"),
            thirdRow(Slot(Key.Page("=\\<", KeyboardPage.Symbols2), weight = 1.5f, muted = true), "*\"':;!?"),
            bottomRow(Key.Page("ABC", KeyboardPage.Letters), char(',', muted = true), '.'),
        )

        KeyboardPage.Symbols2 -> listOf(
            chars("~`|•√π÷×§¶"),
            chars("£¥$¢^°={}\\"),
            thirdRow(Slot(Key.Page("?123", KeyboardPage.Symbols1), weight = 1.5f, muted = true), "%©®™✓[]"),
            bottomRow(Key.Page("ABC", KeyboardPage.Letters), char('<'), '>'),
        )
    }
}
