package dev.ybdn.ciao.clavier.domain.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyVariantsTest {

    @Test
    fun `e offers the French accents first, then the euro sign`() {
        assertEquals(listOf("é", "è", "ê", "ë", "€"), KeyVariants.of('e'))
    }

    @Test
    fun `variants follow the case, symbols are unchanged`() {
        assertEquals(listOf("É", "È", "Ê", "Ë", "€"), KeyVariants.forCase(KeyVariants.of('e'), upper = true))
        assertEquals(listOf("Ô", "Œ", "Ö"), KeyVariants.forCase(KeyVariants.of('o'), upper = true))
        assertEquals(listOf("Ÿ"), KeyVariants.forCase(KeyVariants.of('y'), upper = true))
    }

    @Test
    fun `keys without variants have none`() {
        assertTrue(KeyVariants.of('z').isEmpty())
    }

    @Test
    fun `letters page keys carry their variants`() {
        val slots = KeyboardLayout.rows(KeyboardPage.Letters).flatten()
        val apostrophe = slots.single { it.key == Key.Character('\'', '\'') }
        assertEquals(listOf("’", "«", "»"), apostrophe.variants)
        val comma = slots.single { it.key == Key.Character(',', ',') }
        assertEquals(listOf(";", ":"), comma.variants)
    }
}
