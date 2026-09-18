package dev.ybdn.ciaocloud.domain.util

import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.model.PhoneMedia
import dev.ybdn.ciaocloud.domain.model.SsdMedia
import dev.ybdn.ciaocloud.domain.model.TriageDecision
import dev.ybdn.ciaocloud.domain.model.TriageState
import dev.ybdn.ciaocloud.domain.model.TriageSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TriageRulesTest {

    private val now = 1_000_000_000L

    private fun phone(id: Long, day: LocalDate, taken: Long, size: Long = 10) = GalleryItem(
        key = "phone:$id",
        phone = PhoneMedia(id, "content://media/$id", "IMG_$id.jpg", MediaType.PHOTO, "image/jpeg", size, taken, 0, 0, 0, null, "DCIM/Camera/"),
        ssd = null,
        captureDate = day,
        sortEpochMillis = taken,
    )

    private fun ssdMedia(path: String, day: LocalDate, size: Long = 10) =
        SsdMedia(path, path.substringAfterLast('/'), MediaType.PHOTO, "image/jpeg", size, day, null, 0)

    private fun ssdOnly(path: String, day: LocalDate, size: Long = 10) =
        GalleryItem("ssd:$path", null, ssdMedia(path, day, size), day, null)

    private fun both(id: Long, path: String, day: LocalDate) = phone(id, day, 0).copy(ssd = ssdMedia(path, day))

    private fun state(key: String, decision: TriageDecision, decidedAt: Long = now, snoozeUntil: Long? = null) =
        TriageState(key, decision, decidedAt, snoozeUntil)

    private val day1 = LocalDate.of(2025, 4, 20)
    private val day2 = LocalDate.of(2025, 4, 21)

    @Test
    fun `an item without decision is pending`() {
        assertTrue(TriageRules.isPending(null, now))
    }

    @Test
    fun `kept and queued items leave the pile`() {
        assertFalse(TriageRules.isPending(state("k", TriageDecision.KEPT), now))
        assertFalse(TriageRules.isPending(state("k", TriageDecision.QUEUED_FOR_DELETION), now))
    }

    @Test
    fun `a snoozed item comes back once its snooze has expired`() {
        assertFalse(TriageRules.isPending(state("k", TriageDecision.SNOOZED, snoozeUntil = now + 1), now))
        assertTrue(TriageRules.isPending(state("k", TriageDecision.SNOOZED, snoozeUntil = now), now))
        assertTrue(TriageRules.isPending(state("k", TriageDecision.SNOOZED, snoozeUntil = now - 1), now))
    }

    @Test
    fun `snooze lasts seven days`() {
        assertEquals(now + 7L * 24 * 60 * 60 * 1000, TriageRules.snoozeUntil(now))
    }

    @Test
    fun `pile is grouped by day, most recent first, then by capture instant`() {
        val a = phone(1, day1, taken = 100)
        val b = phone(2, day2, taken = 200)
        val c = phone(3, day2, taken = 300)
        val d = ssdOnly("DCIM/2025/04/21/OLD.jpg", day2)

        val pile = TriageRules.pile(listOf(a, d, b, c), emptyList(), now)

        assertEquals(listOf(c, b, d, a), pile.pending)
    }

    @Test
    fun `pile excludes decided items and lists the deletion queue`() {
        val kept = phone(1, day2, 400)
        val queued = phone(2, day2, 300)
        val snoozed = phone(3, day2, 200)
        val expired = phone(4, day2, 100)
        val fresh = phone(5, day1, 50)
        val states = listOf(
            state("phone:1", TriageDecision.KEPT),
            state("phone:2", TriageDecision.QUEUED_FOR_DELETION),
            state("phone:3", TriageDecision.SNOOZED, snoozeUntil = now + 1),
            state("phone:4", TriageDecision.SNOOZED, snoozeUntil = now - 1),
        )

        val pile = TriageRules.pile(listOf(kept, queued, snoozed, expired, fresh), states, now)

        assertEquals(listOf(expired, fresh), pile.pending)
        assertEquals(listOf(queued), pile.queued)
    }

    @Test
    fun `a backed up item is matched by its ssd key`() {
        val item = both(1, "DCIM/2025/04/21/IMG_1.jpg", day2)

        val pile = TriageRules.pile(listOf(item), listOf(state("ssd:DCIM/2025/04/21/IMG_1.jpg", TriageDecision.KEPT)), now)

        assertEquals(emptyList<GalleryItem>(), pile.pending)
    }

    @Test
    fun `summary counts session decisions and the whole deletion queue`() {
        val items = listOf(phone(1, day2, 0, size = 100), phone(2, day2, 0, size = 20), phone(3, day2, 0), phone(4, day2, 0))
        val sessionStart = now - 10
        val states = listOf(
            state("phone:1", TriageDecision.QUEUED_FOR_DELETION, decidedAt = sessionStart - 1),
            state("phone:2", TriageDecision.QUEUED_FOR_DELETION),
            state("phone:3", TriageDecision.KEPT),
            state("phone:4", TriageDecision.SNOOZED, snoozeUntil = now + 1),
            // Média supprimé depuis la galerie : décision orpheline ignorée.
            state("phone:99", TriageDecision.QUEUED_FOR_DELETION),
        )

        val summary = TriageRules.summary(items, states, sessionStart)

        assertEquals(TriageSummary(kept = 1, queued = 1, queuedBytes = 20, snoozed = 1, totalQueued = 2, totalQueuedBytes = 120), summary)
    }

    @Test
    fun `deletion plan sends every phone item to the trash and ssd only items to definitive deletion`() {
        val notBackedUp = phone(1, day2, 0)
        val backedUp = both(2, "DCIM/2025/04/21/IMG_2.jpg", day2)
        val ssdOnly = ssdOnly("DCIM/2025/04/21/IMG_3.jpg", day2)

        val plan = TriageRules.deletionPlan(listOf(notBackedUp, backedUp, ssdOnly))

        assertEquals(listOf(notBackedUp, backedUp), plan.phone)
        assertEquals(listOf(ssdOnly), plan.ssdOnly)
        assertEquals(1, plan.notBackedUpCount)
        assertEquals(1, plan.backedUpCount)
        assertEquals(2, plan.ssdDeletionCount)
    }
}
