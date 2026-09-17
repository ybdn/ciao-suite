package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.DateShift
import dev.ybdn.ciaocloud.domain.model.FieldChange
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.MediaDetails
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.model.MetadataChanges
import dev.ybdn.ciaocloud.domain.model.MetadataEditPreview
import dev.ybdn.ciaocloud.domain.model.MetadataEditSummary
import dev.ybdn.ciaocloud.domain.model.PhoneMedia
import dev.ybdn.ciaocloud.domain.model.SsdMedia
import dev.ybdn.ciaocloud.domain.model.TransferRecord
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import dev.ybdn.ciaocloud.domain.repository.EditJournalEntry
import dev.ybdn.ciaocloud.domain.repository.MediaDetailsReader
import dev.ybdn.ciaocloud.domain.repository.SsdMoveStep
import dev.ybdn.ciaocloud.domain.repository.TransferActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

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
    private val favorites = FakeFavoritesRepository()
    private val journal = FakeEditJournal()
    private val thumbnails = FakeThumbnailCache()
    private val transferActivity = object : TransferActivity {
        override val isRunning = MutableStateFlow(false)
    }
    private val detailsReader = object : MediaDetailsReader {
        val details = HashMap<String, MediaDetails>()
        override suspend fun read(uri: String, mediaType: MediaType, isMediaStoreUri: Boolean) = details[uri]
    }
    private val editor = SafeFileEditor(
        phoneWriter, ssdWriter, access, journal, transfers, index, thumbnails, favorites, DirectTransactionRunner,
    )

    private fun useCase(ssdAvailable: Boolean = true) = EditMetadataUseCase(
        workspace, workspace, OriginalWorkFiles(workspace, browser),
        GetEditCapabilitiesUseCase(FakeSsdMediaBrowser(available = ssdAvailable), transferActivity),
        GetMediaDetailsUseCase(detailsReader, browser),
        access, editor,
        zoneId = { ZoneOffset.UTC },
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

    /** Média sauvegardé : copie SSD identique, index et enregistrement vérifié. */
    private suspend fun backedUp(id: Long, path: String, dateTimeOriginal: String, offset: String? = null): GalleryItem {
        val item = phoneItem(id, ssdPath = path)
        ssdWriter.files[path] = byteArrayOf(id.toByte(), 1)
        index.upsert(item.ssd!!)
        transfers.upsert(
            TransferRecord(id, MediaType.PHOTO, TransferStatus.VERIFIED, path, fingerprintOf(byteArrayOf(id.toByte(), 1)).checksum, sizeBytes = 2),
        )
        workspace.sourceTags[item.phone!!.uri] = buildMap {
            put("DateTimeOriginal", dateTimeOriginal)
            offset?.let { put("OffsetTimeOriginal", it) }
        }
        return item
    }

    @Test
    fun `changing a text of a backed up photo rewrites both copies and keeps it verified`() = runTest {
        val path = "DCIM/2025/04/21/IMG_1.jpg"
        val item = backedUp(1, path, "2025:04:21 10:00:00")

        val summary = useCase()(listOf(item), description)

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

        val summary = useCase()(items, description) { current, total -> progress += current to total }

        assertEquals(MetadataEditSummary(modified = 2, skipped = 1), summary)
        assertEquals(listOf(listOf(items[0].phone!!.uri, items[2].phone!!.uri)), access.requests)
        assertEquals(listOf(1 to 2, 2 to 2), progress)
    }

    @Test
    fun `refused access modifies nothing`() = runTest {
        val item = phoneItem(1)
        access.accept = false

        assertEquals(MetadataEditSummary(cancelled = true), useCase()(listOf(item), description))
        assertArrayEquals(byteArrayOf(1, 1), phoneWriter.files[item.phone!!.uri])
    }

    @Test
    fun `a failure on one photo does not stop the others`() = runTest {
        val items = listOf(phoneItem(1), phoneItem(2))
        sources.remove(items[0].phone!!.uri)

        val summary = useCase()(items, description)

        assertEquals(1, summary.modified)
        assertEquals(listOf("IMG_1.jpg"), summary.failedNames)
    }

    @Test
    fun `unplugged ssd is reported for an ssd only photo`() = runTest {
        val ssdOnly = GalleryItem(
            "ssd:x", null, SsdMedia("DCIM/2025/04/21/x.jpg", "x.jpg", MediaType.PHOTO, "image/jpeg", 2, day, null, 0), day, null,
        )

        val summary = useCase(ssdAvailable = false)(listOf(ssdOnly), description)

        assertTrue(summary.ssdUnavailable)
        assertEquals(0, summary.modified)
    }

    // Date, fuseau et déplacement sur le SSD

    @Test
    fun `a new date moves the ssd copy to its day folder and follows every reference`() = runTest {
        val path = "dcim/2025/04/21/IMG_1.jpg"
        val item = backedUp(1, path, "2025:04:21 23:30:00", "+02:00")
        favorites.setFavorite(listOf("ssd:$path"), true)
        ssdWriter.files["DCIM/2025/04/22/IMG_1.jpg"] = byteArrayOf(9, 9, 9)

        val summary = useCase()(listOf(item), MetadataChanges(captureDateTime = FieldChange.Set(LocalDateTime.of(2025, 4, 22, 8, 0))))

        val newPath = "DCIM/2025/04/22/IMG_1_1.jpg"
        assertEquals(MetadataEditSummary(modified = 1, moved = 1), summary)
        assertEquals("2025:04:22 08:00:00", workspace.appliedPlans.single().set["DateTimeOriginal"])
        assertNull(ssdWriter.files[path])
        assertArrayEquals(phoneWriter.files[item.phone!!.uri], ssdWriter.files[newPath])
        assertEquals(newPath, transfers.records.value.getValue(1).destinationPath)
        assertEquals(TransferStatus.VERIFIED, transfers.records.value.getValue(1).status)
        assertNull(index.get(path))
        val indexed = index.get(newPath)!!
        assertEquals(LocalDate.of(2025, 4, 22), indexed.captureDate)
        assertEquals(LocalDateTime.of(2025, 4, 22, 6, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), indexed.capturedAtEpochMillis)
        assertEquals(setOf("ssd:$newPath"), favorites.keys.value)
        assertTrue(journal.entries.isEmpty())
    }

    @Test
    fun `an identical file already in the target folder is reused`() = runTest {
        val path = "DCIM/2025/04/21/IMG_1.jpg"
        val item = backedUp(1, path, "2025:04:21 10:00:00")
        favorites.setFavorite(listOf("ssd:$path"), true)
        // Copie identique au fichier une fois sa date modifiée (le faux ajoute un octet par plan).
        val plan = dev.ybdn.ciaocloud.domain.util.ExifWritePlan.from(
            MetadataChanges(dateShift = DateShift(days = 1)),
            dev.ybdn.ciaocloud.domain.model.CaptureTimestamp(LocalDateTime.of(2025, 4, 21, 10, 0)),
        )
        ssdWriter.files["DCIM/2025/04/22/IMG_1.jpg"] = byteArrayOf(plan.hashCode().toByte(), 1, 1)

        val summary = useCase()(listOf(item), MetadataChanges(dateShift = DateShift(days = 1)))

        val existing = "DCIM/2025/04/22/IMG_1.jpg"
        assertEquals(1, summary.moved)
        assertNull(ssdWriter.files[path])
        assertEquals(setOf(existing), ssdWriter.files.keys)
        assertEquals(existing, transfers.records.value.getValue(1).destinationPath)
        assertEquals(setOf("ssd:$existing"), favorites.keys.value)
    }

    @Test
    fun `setting the time zone keeps the local time and a same day change does not move`() = runTest {
        val path = "DCIM/2025/04/21/IMG_1.jpg"
        val item = backedUp(1, path, "2025:04:21 10:00:00", "+02:00")

        val summary = useCase()(listOf(item), MetadataChanges(utcOffsetMinutes = FieldChange.Set(-5 * 60)))

        assertEquals(MetadataEditSummary(modified = 1), summary)
        val set = workspace.appliedPlans.single().set
        assertEquals("-05:00", set["OffsetTimeOriginal"])
        assertNull(set["DateTimeOriginal"])
        assertTrue(path in ssdWriter.files)
        assertEquals(LocalDateTime.of(2025, 4, 21, 15, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), index.get(path)!!.capturedAtEpochMillis)
    }

    @Test
    fun `a phone only photo is never moved`() = runTest {
        val item = phoneItem(1)
        workspace.sourceTags[item.phone!!.uri] = mapOf("DateTimeOriginal" to "2025:04:21 10:00:00")

        val summary = useCase()(listOf(item), MetadataChanges(dateShift = DateShift(days = 3)))

        assertEquals(MetadataEditSummary(modified = 1), summary)
        assertEquals("2025:04:24 10:00:00", workspace.appliedPlans.single().set["DateTimeOriginal"])
    }

    @Test
    fun `a failed move leaves the ssd copy in place and still verified`() = runTest {
        val path = "DCIM/2025/04/21/IMG_1.jpg"
        val item = backedUp(1, path, "2025:04:21 10:00:00")
        ssdWriter.failMove = true

        val summary = useCase()(listOf(item), MetadataChanges(dateShift = DateShift(days = 2)))

        assertEquals(listOf("IMG_1.jpg"), summary.failedNames)
        assertArrayEquals(phoneWriter.files[item.phone!!.uri], ssdWriter.files[path])
        assertEquals(path, transfers.records.value.getValue(1).destinationPath)
        assertEquals(TransferStatus.VERIFIED, transfers.records.value.getValue(1).status)
    }

    @Test
    fun `preview counts edited, skipped and moved photos`() = runTest {
        val moving = backedUp(1, "DCIM/2025/04/21/IMG_1.jpg", "2025:04:21 23:00:00")
        val staying = backedUp(2, "DCIM/2025/04/21/IMG_2.jpg", "2025:04:21 08:00:00")
        detailsReader.details[moving.phone!!.uri] = MediaDetails(captureLocalDateTime = LocalDateTime.of(2025, 4, 21, 23, 0))
        detailsReader.details[staying.phone!!.uri] = MediaDetails(captureLocalDateTime = LocalDateTime.of(2025, 4, 21, 8, 0))

        val preview = useCase().preview(
            listOf(moving, staying, phoneItem(3, "anim.gif", "image/gif")),
            MetadataChanges(dateShift = DateShift(hours = 2)),
        )

        assertEquals(MetadataEditPreview(editable = 2, skipped = 1, toMove = 1), preview)
    }

    @Test
    fun `an interrupted move is completed at recovery`() = runTest {
        val from = "DCIM/2025/04/21/IMG_1.jpg"
        val to = "DCIM/2025/04/22/IMG_1.jpg"
        backedUp(1, from, "2025:04:22 10:00:00")
        favorites.setFavorite(listOf("ssd:$from"), true)
        // Fichier déplacé, application crashée avant la mise à jour des références.
        ssdWriter.files[to] = ssdWriter.files.remove(from)!!
        journal.write(EditJournalEntry("move", move = SsdMoveStep(from, to, duplicate = false, capturedAtEpochMillis = 42)))

        assertEquals(0, RecoverInterruptedEditsUseCase(journal, editor)())

        assertEquals(to, transfers.records.value.getValue(1).destinationPath)
        assertEquals(42L, index.get(to)!!.capturedAtEpochMillis)
        assertEquals(setOf("ssd:$to"), favorites.keys.value)
    }

    @Test
    fun `an interrupted move that did not happen changes nothing`() = runTest {
        val from = "DCIM/2025/04/21/IMG_1.jpg"
        val to = "DCIM/2025/04/22/IMG_1.jpg"
        backedUp(1, from, "2025:04:22 10:00:00")
        ssdWriter.files["$to.ciao-new"] = byteArrayOf(1)
        journal.write(EditJournalEntry("move", move = SsdMoveStep(from, to, duplicate = false, capturedAtEpochMillis = 42)))

        RecoverInterruptedEditsUseCase(journal, editor)()

        assertEquals(setOf(from), ssdWriter.files.keys)
        assertEquals(from, transfers.records.value.getValue(1).destinationPath)
        assertTrue(journal.entries.isEmpty())
    }
}
