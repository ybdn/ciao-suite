package dev.ybdn.ciao.clavier.domain.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardRulesTest {

    private val hour = ClipboardRetention.OneHour.millis
    private val now = 10 * ClipboardRetention.SevenDays.millis

    private fun item(id: Long, age: Long, pinned: Boolean = false) =
        ClipboardItem(id = id, text = "t$id", copiedAt = now - age, pinned = pinned)

    @Test
    fun `only non-blank, non-sensitive, reasonably sized text is kept`() {
        assertTrue(ClipboardRules.accepts("Bonjour", isSensitive = false))
        assertFalse(ClipboardRules.accepts("motdepasse", isSensitive = true))
        assertFalse(ClipboardRules.accepts("   ", isSensitive = false))
        assertFalse(ClipboardRules.accepts(null, isSensitive = false))
        assertFalse(ClipboardRules.accepts("x".repeat(ClipboardRules.MaxTextLength + 1), isSensitive = false))
    }

    @Test
    fun `unpinned items expire after the retention, pinned ones never`() {
        assertFalse(ClipboardRules.isExpired(item(1, hour - 1), now, ClipboardRetention.OneHour))
        assertTrue(ClipboardRules.isExpired(item(1, hour + 1), now, ClipboardRetention.OneHour))
        assertFalse(ClipboardRules.isExpired(item(1, hour + 1), now, ClipboardRetention.OneDay))
        assertFalse(ClipboardRules.isExpired(item(1, 30 * hour * 24, pinned = true), now, ClipboardRetention.OneHour))
    }

    @Test
    fun `beyond 25 items, the oldest unpinned ones go`() {
        val pinnedOld = item(100, age = 50 * 1000L, pinned = true)
        val items = (1L..26L).map { item(it, age = it * 1000L) } + pinnedOld
        val removed = ClipboardRules.toRemove(items, now, ClipboardRetention.OneHour)
        assertEquals(listOf(26L, 25L), removed.map { it.id })
    }

    @Test
    fun `expired items are removed even under the limit`() {
        val items = listOf(item(1, 10), item(2, hour * 2), item(3, hour * 2, pinned = true))
        assertEquals(listOf(2L), ClipboardRules.toRemove(items, now, ClipboardRetention.OneHour).map { it.id })
    }

    @Test
    fun `display puts pinned items first, then the most recent`() {
        val items = listOf(item(1, 3000), item(2, 1000), item(3, 5000, pinned = true), item(4, hour * 2))
        assertEquals(listOf(3L, 2L, 1L), ClipboardRules.displayed(items, now, ClipboardRetention.OneHour).map { it.id })
    }

    @Test
    fun `the paste chip offers the latest copy for one minute`() {
        assertEquals(2L, ClipboardRules.pasteChip(listOf(item(1, 30_000), item(2, 10_000)), now)?.id)
        assertNull(ClipboardRules.pasteChip(listOf(item(1, ClipboardRules.PasteChipMillis + 1)), now))
        assertNull(ClipboardRules.pasteChip(emptyList(), now))
    }
}
