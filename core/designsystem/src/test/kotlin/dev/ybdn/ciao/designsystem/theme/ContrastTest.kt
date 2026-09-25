package dev.ybdn.ciao.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrastes de la palette (docs/design-system.md, ADR 0003 ; règle explicite du clavier,
 * apps/clavier/docs/spec-v1.md §5.4) : noir (ou crème en sombre) sur chaque accent, et texte sur
 * fond, dans les deux thèmes. Formule WCAG 2 (relativeluminance.com), en Kotlin pur : pas de
 * `Color.luminance()` d'Android, pour rester exécutable en test unitaire JVM.
 */
class ContrastTest {

    private fun linearChannel(c: Float): Double {
        val cs = c.toDouble()
        return if (cs <= 0.03928) cs / 12.92 else Math.pow((cs + 0.055) / 1.055, 2.4)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearChannel(color.red) + 0.7152 * linearChannel(color.green) + 0.0722 * linearChannel(color.blue)

    private fun contrastRatio(a: Color, b: Color): Double {
        val l1 = relativeLuminance(a) + 0.05
        val l2 = relativeLuminance(b) + 0.05
        return if (l1 > l2) l1 / l2 else l2 / l1
    }

    private val accents = listOf(Yellow, Pink, Sky, Green, Orange, Lavender)

    /** Chaque accent porte toujours du texte Ink (Color.kt) : au moins 7,5:1 (spec clavier §5.4). */
    @Test
    fun `ink on every accent clears the 7,5 to 1 minimum`() {
        accents.forEach { accent ->
            val ratio = contrastRatio(Ink, accent)
            assertTrue("Ink sur $accent : ratio $ratio", ratio >= 7.5)
        }
    }

    /** Libellés et suggestions : au moins l'AA de WCAG (4,5:1), dans les deux thèmes. */
    @Test
    fun `body text on the page clears WCAG AA in both themes`() {
        listOf(LightPalette, DarkPalette).forEach { palette ->
            val ratio = contrastRatio(palette.content, palette.page)
            assertTrue("texte sur fond : ratio $ratio", ratio >= 4.5)
        }
    }
}
