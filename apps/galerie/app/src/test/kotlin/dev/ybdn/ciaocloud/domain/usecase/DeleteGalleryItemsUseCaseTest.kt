package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.model.PhoneMedia
import dev.ybdn.ciaocloud.domain.model.SsdMedia
import dev.ybdn.ciaocloud.domain.model.TransferRecord
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DeleteGalleryItemsUseCaseTest {

    private val path = "DCIM/2025/04/21/IMG_1.jpg"
    private val trash = FakeMediaTrash()
    private val browser = FakeSsdMediaBrowser()
    private val index = FakeSsdMediaIndex()
    private val transfers = FakeTransferStateRepository()
    private val favorites = FakeFavoritesRepository(setOf("ssd:$path"))
    private val useCase = DeleteGalleryItemsUseCase(
        trash, browser, index, FakeThumbnailCache(), transfers, favorites, DirectTransactionRunner,
    )

    private val phone = PhoneMedia(1, "content://media/1", "IMG_1.jpg", MediaType.PHOTO, "image/jpeg", 3, 0, 0, 0, 0, null, "DCIM/Camera/")
    private val ssd = SsdMedia(path, "IMG_1.jpg", MediaType.PHOTO, "image/jpeg", 3, LocalDate.of(2025, 4, 21), null, 0)
    private val backedUp = GalleryItem("phone:1", phone, ssd, LocalDate.of(2025, 4, 21), 0)

    private suspend fun setUpBackedUp() {
        index.upsert(ssd)
        transfers.upsert(TransferRecord(1, MediaType.PHOTO, TransferStatus.VERIFIED, destinationPath = path))
    }

    @Test
    fun `deleting the ssd copy of a phone media invalidates its verified state`() = runTest {
        setUpBackedUp()

        val outcome = useCase(listOf(backedUp), DeleteTarget.SSD)

        assertEquals(DeleteItemsOutcome.Done(phoneTrashed = 0, ssdDeleted = 1, ssdFailed = 0), outcome)
        assertNull(transfers.records.value[1])
        assertNull(index.get(path))
        assertEquals(setOf("phone:1"), favorites.keys.value)
        assertEquals(emptyList<String>(), trash.trashed)
    }

    @Test
    fun `trashing the phone copy marks the record deleted and keeps the ssd favorite`() = runTest {
        setUpBackedUp()

        useCase(listOf(backedUp), DeleteTarget.PHONE)

        assertEquals(TransferStatus.DELETED, transfers.records.value[1]!!.status)
        assertEquals(setOf("ssd:$path"), favorites.keys.value)
        assertEquals(emptyList<String>(), browser.deleted)
    }

    @Test
    fun `a refused system confirmation deletes nothing, not even on the ssd`() = runTest {
        setUpBackedUp()
        trash.accept = false

        val outcome = useCase(listOf(backedUp), DeleteTarget.EVERYWHERE)

        assertEquals(DeleteItemsOutcome.Cancelled, outcome)
        assertEquals(emptyList<String>(), browser.deleted)
        assertEquals(TransferStatus.VERIFIED, transfers.records.value[1]!!.status)
    }

    @Test
    fun `nothing is deleted when the ssd is unplugged`() = runTest {
        val unpluggedUseCase = DeleteGalleryItemsUseCase(
            trash, FakeSsdMediaBrowser(available = false), index, FakeThumbnailCache(), transfers, favorites, DirectTransactionRunner,
        )

        assertEquals(DeleteItemsOutcome.SsdUnavailable, unpluggedUseCase(listOf(backedUp), DeleteTarget.EVERYWHERE))
        assertEquals(emptyList<String>(), trash.trashed)
    }
}
