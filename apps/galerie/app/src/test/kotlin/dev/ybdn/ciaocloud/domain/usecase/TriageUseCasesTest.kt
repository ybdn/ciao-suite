package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.model.PhoneMedia
import dev.ybdn.ciaocloud.domain.model.SsdMedia
import dev.ybdn.ciaocloud.domain.model.TransferRecord
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import dev.ybdn.ciaocloud.domain.model.TriageDecision
import dev.ybdn.ciaocloud.domain.model.TriageState
import dev.ybdn.ciaocloud.domain.util.TriageRules
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TriageUseCasesTest {

    private val now = 5_000L
    private val day = LocalDate.of(2025, 4, 21)
    private val path = "DCIM/2025/04/21/IMG_2.jpg"
    private val triage = FakeTriageRepository()
    private val trash = FakeMediaTrash()
    private val browser = FakeSsdMediaBrowser()
    private val index = FakeSsdMediaIndex()
    private val transfers = FakeTransferStateRepository()

    private fun phoneMedia(id: Long) =
        PhoneMedia(id, "content://media/$id", "IMG_$id.jpg", MediaType.PHOTO, "image/jpeg", 3, 0, 0, 0, 0, null, "DCIM/Camera/")

    private val notBackedUp = GalleryItem("phone:1", phoneMedia(1), null, day, 0)
    private val ssd = SsdMedia(path, "IMG_2.jpg", MediaType.PHOTO, "image/jpeg", 3, day, null, 0)
    private val backedUp = GalleryItem("phone:2", phoneMedia(2), ssd, day, 0)

    private val deleteQueued = DeleteQueuedTriageItemsUseCase(
        DeleteGalleryItemsUseCase(
            trash, browser, index, FakeThumbnailCache(), transfers, FakeFavoritesRepository(), triage, DirectTransactionRunner,
        ),
        triage,
    )

    @Test
    fun `recording a snooze stores its expiry and undo forgets the decision`() = runTest {
        val key = RecordTriageDecisionUseCase(triage) { now }(backedUp, TriageDecision.SNOOZED)

        assertEquals("ssd:$path", key)
        assertEquals(TriageState(key, TriageDecision.SNOOZED, now, TriageRules.snoozeUntil(now)), triage.states.value[key])

        UndoLastTriageDecisionUseCase(triage)(key)

        assertNull(triage.states.value[key])
    }

    @Test
    fun `kept decision has no snooze date`() = runTest {
        val key = RecordTriageDecisionUseCase(triage) { now }(notBackedUp, TriageDecision.KEPT)

        assertNull(triage.states.value[key]!!.snoozeUntilEpochMillis)
    }

    @Test
    fun `reset keeps the deletion queue`() = runTest {
        triage.upsert(TriageState("phone:1", TriageDecision.KEPT, now, null))
        triage.upsert(TriageState("phone:2", TriageDecision.SNOOZED, now, now + 1))
        triage.upsert(TriageState("phone:3", TriageDecision.QUEUED_FOR_DELETION, now, null))

        ResetTriageUseCase(triage)()

        assertEquals(setOf("phone:3"), triage.states.value.keys)
    }

    @Test
    fun `confirmed deletions remove every place and forget the queue`() = runTest {
        index.upsert(ssd)
        transfers.upsert(TransferRecord(2, MediaType.PHOTO, TransferStatus.VERIFIED, destinationPath = path))
        triage.upsert(TriageState("phone:1", TriageDecision.QUEUED_FOR_DELETION, now, null))
        triage.upsert(TriageState("ssd:$path", TriageDecision.QUEUED_FOR_DELETION, now, null))

        val outcome = deleteQueued(listOf(notBackedUp, backedUp))

        assertEquals(DeleteItemsOutcome.Done(2, 1, 0, setOf("phone:1", "phone:2")), outcome)
        assertEquals(listOf("content://media/1", "content://media/2"), trash.trashed)
        assertEquals(listOf(path), browser.deleted)
        assertEquals(emptySet<String>(), triage.states.value.keys)
    }

    @Test
    fun `a refused system confirmation keeps the queue`() = runTest {
        trash.accept = false
        triage.upsert(TriageState("phone:1", TriageDecision.QUEUED_FOR_DELETION, now, null))

        val outcome = deleteQueued(listOf(notBackedUp))

        assertEquals(DeleteItemsOutcome.Cancelled, outcome)
        assertEquals(setOf("phone:1"), triage.states.value.keys)
    }

    @Test
    fun `an unplugged ssd deletes nothing and keeps the queue`() = runTest {
        val unplugged = DeleteQueuedTriageItemsUseCase(
            DeleteGalleryItemsUseCase(
                trash, FakeSsdMediaBrowser(available = false), index, FakeThumbnailCache(), transfers,
                FakeFavoritesRepository(), triage, DirectTransactionRunner,
            ),
            triage,
        )
        triage.upsert(TriageState("ssd:$path", TriageDecision.QUEUED_FOR_DELETION, now, null))

        assertEquals(DeleteItemsOutcome.SsdUnavailable, unplugged(listOf(backedUp)))
        assertEquals(emptyList<String>(), trash.trashed)
        assertEquals(setOf("ssd:$path"), triage.states.value.keys)
    }
}
