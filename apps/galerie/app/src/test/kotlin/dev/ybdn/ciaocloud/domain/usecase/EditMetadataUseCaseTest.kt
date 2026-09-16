package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.FieldChange
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.model.MetadataChanges
import dev.ybdn.ciaocloud.domain.model.MetadataEditSummary
import dev.ybdn.ciaocloud.domain.model.PhoneMedia
import dev.ybdn.ciaocloud.domain.model.SsdMedia
import dev.ybdn.ciaocloud.domain.model.TransferRecord
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import dev.ybdn.ciaocloud.domain.repository.TransferActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EditMetadataUseCaseTest {

    private val day = LocalDate.of(2025, 4, 21)
    private val sources = HashMap<String, ByteArray>()
    private val workspace = FakeEditWorkspace(sources)
    private val phoneWriter = FakePhoneWriter(workspace.files)
    private val ssdWriter = FakeSsdWriter(workspace.files)
    private val access = FakeWriteAccess()
    private val transfers = FakeTransferStateRepository()
    private val index = FakeSsdMediaIndex()
    private val browser = FakeSsdMediaBrowser()
    private val transferActivity = object : TransferActivity {
        override val isRunning = MutableStateFlow(false)
    }
    private val editor = SafeFileEditor(
        phoneWriter, ssdWriter, access, FakeEditJournal(), transfers, index, FakeThumbnailCache(), DirectTransactionRunner,
    )
    private val useCase = EditMetadataUseCase(
        workspace, workspace, OriginalWorkFiles(workspace, browser),
        GetEditCapabilitiesUseCase(browser, transferActivity), access, editor,
    )
    private val description = MetadataChanges(description = FieldChange.Set("Tour Eiffel à Paris"))

    private fun phoneItem(id: Long, name: String = "IMG_$id.jpg", mime: String = "image/jpeg", ssdPath: String? = null): GalleryItem {
        val uri = "content://media/external/images/media/$id"
        val bytes = byteArrayOf(id.toByte(), 1)
        sources[uri] = bytes.copyOf()
        phoneWriter.files[uri] = bytes.copyOf()
        val ssd = ssdPath?.let { SsdMedia(it, name, MediaType.PHOTO, mime, 2, day, null, 0) }
        return GalleryItem(
            "phone:$id",
            PhoneMedia(id, uri, name, MediaType.PHOTO, mime, 2, 0, 0, 10, 10, null, "DCIM/Camera/"),
            ssd,
            day,
            0,
        )
    }

    @Test
    fun `changing a text of a backed up photo rewrites both copies and keeps it verified`() = runTest {
        val path = "DCIM/2025/04/21/IMG_1.jpg"
        val item = phoneItem(1, ssdPath = path)
        ssdWriter.files[path] = byteArrayOf(1, 1)
        index.upsert(item.ssd!!)
        transfers.upsert(
            TransferRecord(1, MediaType.PHOTO, TransferStatus.VERIFIED, path, fingerprintOf(byteArrayOf(1, 1)).checksum, sizeBytes = 2),
        )

        val summary = useCase(listOf(item), description)

        assertEquals(MetadataEditSummary(modified = 1), summary)
        assertArrayEquals(phoneWriter.files[item.phone!!.uri], ssdWriter.files[path])
        val record = transfers.records.value.getValue(1)
        assertEquals(TransferStatus.VERIFIED, record.status)
        assertEquals(fingerprintOf(ssdWriter.files.getValue(path)).checksum, record.checksum)
        assertEquals(mapOf("ImageDescription" to "Tour Eiffel à Paris"), workspace.appliedPlans.single().set)
    }

    @Test
    fun `a group asks write access once and skips unsupported formats`() = runTest {
        val items = listOf(phoneItem(1), phoneItem(2, "IMG_2.heic", "image/heic"), phoneItem(3))
        val progress = mutableListOf<Pair<Int, Int>>()

        val summary = useCase(items, description) { current, total -> progress += current to total }

        assertEquals(MetadataEditSummary(modified = 2, skipped = 1), summary)
        assertEquals(listOf(listOf(items[0].phone!!.uri, items[2].phone!!.uri)), access.requests)
        assertEquals(listOf(1 to 2, 2 to 2), progress)
    }

    @Test
    fun `refused access modifies nothing`() = runTest {
        val item = phoneItem(1)
        access.accept = false

        assertEquals(MetadataEditSummary(cancelled = true), useCase(listOf(item), description))
        assertArrayEquals(byteArrayOf(1, 1), phoneWriter.files[item.phone!!.uri])
    }

    @Test
    fun `a failure on one photo does not stop the others`() = runTest {
        val items = listOf(phoneItem(1), phoneItem(2))
        sources.remove(items[0].phone!!.uri)

        val summary = useCase(items, description)

        assertEquals(1, summary.modified)
        assertEquals(listOf("IMG_1.jpg"), summary.failedNames)
    }

    @Test
    fun `unplugged ssd is reported for an ssd only photo`() = runTest {
        val ssdOnly = GalleryItem(
            "ssd:x", null, SsdMedia("DCIM/2025/04/21/x.jpg", "x.jpg", MediaType.PHOTO, "image/jpeg", 2, day, null, 0), day, null,
        )
        val unplugged = EditMetadataUseCase(
            workspace, workspace, OriginalWorkFiles(workspace, browser),
            GetEditCapabilitiesUseCase(FakeSsdMediaBrowser(available = false), transferActivity), access, editor,
        )

        val summary = unplugged(listOf(ssdOnly), description)

        assertTrue(summary.ssdUnavailable)
        assertEquals(0, summary.modified)
    }
}
