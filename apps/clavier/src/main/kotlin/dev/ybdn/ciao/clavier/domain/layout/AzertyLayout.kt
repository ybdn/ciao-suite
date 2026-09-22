package dev.ybdn.ciao.clavier.domain.layout

/**
 * Disposition AZERTY, page lettres uniquement (apps/clavier/docs/spec-v1.md §6.1).
 * Chaque touche porte un poids relatif (largeur dans sa rangée), repris de la maquette : les trois
 * premières rangées totalisent 10, la dernière 10,5 (barre d'espace de 4,5).
 */
object AzertyLayout {

    data class Slot(val key: Key, val weight: Float = 1f)

    private fun letter(lower: Char, upper: Char) = Slot(Key.Letter(lower, upper))

    val row1: List<Slot> = "azertyuiop".map { letter(it, it.uppercaseChar()) }

    val row2: List<Slot> = "qsdfghjklm".map { letter(it, it.uppercaseChar()) }

    val row3: List<Slot> = listOf(
        Slot(Key.Shift, weight = 1.5f),
    ) + "wxcvbn".map { letter(it, it.uppercaseChar()) } + listOf(
        letter('\'', '\''),
        Slot(Key.Backspace, weight = 1.5f),
    )

    val row4: List<Slot> = listOf(
        Slot(Key.Symbols, weight = 1.5f),
        Slot(Key.Comma),
        Slot(Key.Emoji),
        Slot(Key.Space, weight = 4.5f),
        Slot(Key.Period),
        Slot(Key.Enter, weight = 1.5f),
    )

    val rows: List<List<Slot>> = listOf(row1, row2, row3, row4)
}
