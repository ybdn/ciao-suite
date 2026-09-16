package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.EditRecipe
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.ImageTransform
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.model.PhoneMedia
import dev.ybdn.ciaocloud.domain.model.SaveEditOutcome
import dev.ybdn.ciaocloud.domain.model.SaveMode
import dev.ybdn.ciaocloud.domain.model.SsdMedia
import dev.ybdn.ciaocloud.domain.model.TransferRecord
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import dev.ybdn.ciaocloud.domain.repository.EditJournalEntry
import dev.ybdn.ciaocloud.domain.repository.EditStepKind
import dev.ybdn.ciaocloud.domain.repository.PhoneEditStep
import dev.ybdn.ciaocloud.domain.repository.SsdEditStep
import dev.ybdn.ciaocloud.domain.util.EditCapabilitiesPolicy
import dev.ybdn.ciaocloud.domain.util.EditContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SavePhotoEditUseCaseTest {

    private val phoneUri = "content://media/external/images/media/1"
    private val ssdPath = "DCIM/2025/04/21/IMG_1.jpg"
    /** Dernier octet = orientation EXIF. */
    private val original = byteArrayOf(10, 20, 30, 1)
    private val rotated = byteArrayOf(10, 20, 30, 8)

    private val sources = HashMap<String, ByteArray>()
    private val workspace = FakeEditWorkspace(sources)
    private val phoneWriter = FakePhoneWriter(workspace.files)
    private val ssdWriter = FakeSsdWriter(workspace.files)
    private val access = FakeWriteAccess()
    private val journal = FakeEditJournal()
    private val transfers = FakeTransferStateRepository()
    private val index = FakeSsdMediaIndex()
    private val thumbnails = FakeThumbnailCache()
    private val browser = FakeSsdMediaBrowser()
    private var ids = 0
    private val editor = SafeFileEditor(
        phoneWriter, ssdWriter, access, journal, transfers, index, thumbnails, DirectTransactionRunner,
        newId = { "op${ids++}" }, clock = { 99 },
    )
    private val useCase = SavePhotoEditUseCase(workspace, workspace, OriginalWorkFiles(workspace, browser), editor)

    private val day = LocalDate.of(2025, 4, 21)
    private val phone = PhoneMedia(1, phoneUri, "IMG_1.jpg", MediaType.PHOTO, "image/jpeg", 4, 0, 0, 100, 100, null, "DCIM/Camera/")
    private val ssd = SsdMedia(ssdPath, "IMG_1.jpg", MediaType.PHOTO, "image/jpeg", 4, day, null, 0)
    private val rotateCcw = EditRecipe(ImageTransform.IDENTITY.rotatedCounterClockwise())
    private val verified = TransferRecord(1, MediaType.PHOTO, TransferStatus.VERIFIED, ssdPath, fingerprintOf(original).checksum, sizeBytes = 4)

    private fun item(phone: PhoneMedia?, ssd: SsdMedia?) =
        GalleryItem(phone?.let { "phone:${it.mediaStoreId}" } ?: "ssd:${ssd!!.relativePath}", phone, ssd, day, 0)

    private fun capabilities(item: GalleryItem) = EditCapabilitiesPolicy.capabilities(
        EditContext(item.location, item.mediaType, item.mimeType, ssdAvailable = ssdWriter.available, transferRunning = false),
    )

    private suspend fun givenBackedUp() {
        sources[phoneUri] = original.copyOf()
        phoneWriter.files[phoneUri] = original.copyOf()
        ssdWriter.files[ssdPath] = original.copyOf()
        index.upsert(ssd)
        transfers.upsert(verified)
    }

    private suspend fun save(item: GalleryItem, mode: SaveMode) = useCase(item, rotateCcw, mode, capabilities(item))

    @Test
    fun `replacing a backed up photo updates both files and keeps it verified`() = runTest {
        givenBackedUp()
        // Autre média du téléphone reconnu comme doublon de la même copie SSD.
        transfers.upsert(verified.copy(mediaStoreId = 2))

        val outcome = save(item(phone, ssd), SaveMode.REPLACE)

        assertEquals(SaveEditOutcome.Replaced, outcome)
        assertArrayEquals(rotated, phoneWriter.files[phoneUri])
        assertArrayEquals(rotated, ssdWriter.files[ssdPath])
        assertEquals(setOf(ssdPath), ssdWriter.files.keys)
        assertEquals(verified.copy(checksum = fingerprintOf(rotated).checksum), transfers.records.value[1])
        // Sa copie ne lui est plus identique : il ne doit plus pouvoir être supprimé comme « vérifié ».
        assertNull(transfers.records.value[2])
        assertEquals(99L, index.get(ssdPath)!!.lastModifiedEpochMillis)
        assertEquals(listOf(ssdPath), thumbnails.removed)
        assertTrue(journal.entries.isEmpty())
        assertTrue(workspace.files.isEmpty())
    }

    @Test
    fun `refused write access touches nothing`() = runTest {
        givenBackedUp()
        access.accept = false

        assertEquals(SaveEditOutcome.Cancelled, save(item(phone, ssd), SaveMode.REPLACE))

        assertArrayEquals(original, phoneWriter.files[phoneUri])
        assertArrayEquals(original, ssdWriter.files[ssdPath])
        assertEquals(verified, transfers.records.value[1])
        assertTrue(journal.written.isEmpty())
    }

    @Test
    fun `phone failure after the ssd write removes the transfer record`() = runTest {
        givenBackedUp()
        phoneWriter.failReplace = true

        val outcome = save(item(phone, ssd), SaveMode.REPLACE)

        assertTrue(outcome is SaveEditOutcome.Failed)
        assertArrayEquals(original, phoneWriter.files[phoneUri])
        assertArrayEquals(rotated, ssdWriter.files[ssdPath])
        assertNull(transfers.records.value[1])
        assertTrue(journal.entries.isEmpty())
    }

    @Test
    fun `ssd failure leaves both originals and restores the record`() = runTest {
        givenBackedUp()
        ssdWriter.failRenameOnceTo += "IMG_1.jpg"

        val outcome = save(item(phone, ssd), SaveMode.REPLACE)

        assertTrue(outcome is SaveEditOutcome.Failed)
        assertArrayEquals(original, phoneWriter.files[phoneUri])
        assertArrayEquals(original, ssdWriter.files[ssdPath])
        assertEquals(setOf(ssdPath), ssdWriter.files.keys)
        assertEquals(verified, transfers.records.value[1])
    }

    @Test
    fun `an ssd original stuck under its old name stays journaled until recovered`() = runTest {
        givenBackedUp()
        ssdWriter.failRenameTo += "IMG_1.jpg"

        assertTrue(save(item(phone, ssd), SaveMode.REPLACE) is SaveEditOutcome.Failed)

        assertEquals(setOf("$ssdPath.ciao-old"), ssdWriter.files.keys)
        assertNull(transfers.records.value[1])
        assertEquals(1, journal.entries.size)

        ssdWriter.failRenameTo.clear()
        assertEquals(0, recover())

        assertArrayEquals(original, ssdWriter.files[ssdPath])
        assertEquals(setOf(ssdPath), ssdWriter.files.keys)
        assertEquals(verified, transfers.records.value[1])
    }

    @Test
    fun `failed restoration keeps the operation journaled`() = runTest {
        givenBackedUp()
        phoneWriter.failReplace = true
        phoneWriter.failRestore = true

        save(item(phone, ssd), SaveMode.REPLACE)

        assertNull(transfers.records.value[1])
        assertEquals(1, journal.entries.size)
    }

    @Test
    fun `unplugged ssd is required to replace a backed up photo`() = runTest {
        givenBackedUp()
        ssdWriter.available = false

        val outcome = save(item(phone, ssd), SaveMode.REPLACE)

        assertTrue(outcome is SaveEditOutcome.Unavailable)
        assertArrayEquals(original, phoneWriter.files[phoneUri])
    }

    @Test
    fun `replacing a phone only photo forgets a stale transfer record`() = runTest {
        givenBackedUp()

        val outcome = save(item(phone, null), SaveMode.REPLACE)

        assertEquals(SaveEditOutcome.Replaced, outcome)
        assertArrayEquals(rotated, phoneWriter.files[phoneUri])
        assertArrayEquals(original, ssdWriter.files[ssdPath])
        assertNull(transfers.records.value[1])
    }

    @Test
    fun `replacing an ssd only photo forgets records of deleted originals`() = runTest {
        givenBackedUp()
        transfers.upsert(verified.copy(status = TransferStatus.DELETED))
        sources["content://ssd/$ssdPath"] = original.copyOf()

        val outcome = save(item(null, ssd), SaveMode.REPLACE)

        assertEquals(SaveEditOutcome.Replaced, outcome)
        assertArrayEquals(rotated, ssdWriter.files[ssdPath])
        assertNull(transfers.records.value[1])
        assertTrue(access.requests.isEmpty())
    }

    @Test
    fun `a phone copy is named with the edit suffix and left unsaved`() = runTest {
        givenBackedUp()
        phoneWriter.names["DCIM/Camera/"] = mutableSetOf("IMG_1.jpg", "img_1_EDIT.jpg")

        val outcome = save(item(phone, ssd), SaveMode.COPY) as SaveEditOutcome.Copied

        val copyUri = "content://media/external/images/media/${outcome.key.removePrefix("phone:")}"
        assertArrayEquals(rotated, phoneWriter.files[copyUri])
        assertTrue("IMG_1_edit_1.jpg" in phoneWriter.names.getValue("DCIM/Camera/"))
        assertArrayEquals(original, phoneWriter.files[phoneUri])
        assertEquals(verified, transfers.records.value[1])
        assertEquals(1, transfers.records.value.size)
        assertTrue(access.requests.isEmpty())
    }

    @Test
    fun `an ssd copy is written next to the original and indexed`() = runTest {
        givenBackedUp()
        sources["content://ssd/$ssdPath"] = original.copyOf()

        val outcome = save(item(null, ssd), SaveMode.COPY)

        val copyPath = "DCIM/2025/04/21/IMG_1_edit.jpg"
        assertEquals(SaveEditOutcome.Copied("ssd:$copyPath"), outcome)
        assertArrayEquals(rotated, ssdWriter.files[copyPath])
        assertEquals(4L, index.get(copyPath)!!.sizeBytes)
        assertEquals(setOf(ssdPath, copyPath), ssdWriter.files.keys)
    }

    @Test
    fun `a failed ssd copy leaves no file behind`() = runTest {
        givenBackedUp()
        sources["content://ssd/$ssdPath"] = original.copyOf()
        ssdWriter.failRenameTo += "IMG_1_edit.jpg"

        assertTrue(save(item(null, ssd), SaveMode.COPY) is SaveEditOutcome.Failed)

        assertEquals(setOf(ssdPath), ssdWriter.files.keys)
        assertNull(index.get("DCIM/2025/04/21/IMG_1_edit.jpg"))
    }

    // Reprise après interruption

    private val newFingerprint = fingerprintOf(rotated)

    private fun bothEntry() = EditJournalEntry(
        id = "crash",
        phone = PhoneEditStep(EditStepKind.REPLACE, phoneUri, newFingerprint, fingerprintOf(original).checksum, "backup/crash"),
        ssd = SsdEditStep(EditStepKind.REPLACE, ssdPath, newFingerprint, fingerprintOf(original).checksum),
        previousRecords = listOf(verified),
        editedMediaStoreId = 1,
    )

    private suspend fun givenInterrupted(entry: EditJournalEntry = bothEntry()) {
        givenBackedUp()
        journal.write(entry)
        transfers.delete(1)
    }

    private suspend fun recover() = RecoverInterruptedEditsUseCase(journal, editor)()

    @Test
    fun `recovery deletes an orphan new file and restores the record`() = runTest {
        givenInterrupted()
        ssdWriter.files["$ssdPath.ciao-new"] = rotated.copyOf()

        assertEquals(0, recover())

        assertEquals(setOf(ssdPath), ssdWriter.files.keys)
        assertArrayEquals(original, ssdWriter.files[ssdPath])
        assertEquals(verified, transfers.records.value[1])
        assertTrue(journal.entries.isEmpty())
    }

    @Test
    fun `recovery renames back an old file whose replacement is missing`() = runTest {
        givenInterrupted()
        ssdWriter.files["$ssdPath.ciao-old"] = ssdWriter.files.remove(ssdPath)!!
        ssdWriter.files["$ssdPath.ciao-new"] = rotated.copyOf()

        recover()

        assertEquals(setOf(ssdPath), ssdWriter.files.keys)
        assertArrayEquals(original, ssdWriter.files[ssdPath])
        assertEquals(verified, transfers.records.value[1])
    }

    @Test
    fun `recovery after the ssd swap removes the old file and forgets the record`() = runTest {
        givenInterrupted()
        ssdWriter.files["$ssdPath.ciao-old"] = original.copyOf()
        ssdWriter.files[ssdPath] = rotated.copyOf()

        recover()

        assertEquals(setOf(ssdPath), ssdWriter.files.keys)
        assertArrayEquals(rotated, ssdWriter.files[ssdPath])
        // Téléphone resté à l'original, SSD modifié : plus identiques.
        assertNull(transfers.records.value[1])
    }

    @Test
    fun `recovery restores a partially written phone original from its backup`() = runTest {
        givenInterrupted()
        ssdWriter.files[ssdPath] = rotated.copyOf()
        phoneWriter.backups["backup/crash"] = original.copyOf()
        phoneWriter.files[phoneUri] = byteArrayOf(10, 20)

        recover()

        assertArrayEquals(original, phoneWriter.files[phoneUri])
        assertTrue(phoneWriter.backups.isEmpty())
        assertNull(transfers.records.value[1])
    }

    @Test
    fun `recovery of a completed replacement keeps the media verified`() = runTest {
        givenInterrupted()
        ssdWriter.files[ssdPath] = rotated.copyOf()
        phoneWriter.files[phoneUri] = rotated.copyOf()

        recover()

        assertEquals(verified.copy(checksum = newFingerprint.checksum), transfers.records.value[1])
    }

    @Test
    fun `recovery deletes a pending phone copy`() = runTest {
        givenBackedUp()
        val uri = phoneWriter.createPending("DCIM/Camera/", "IMG_1_edit.jpg", "image/jpeg", null)
        journal.write(EditJournalEntry("copy", phone = PhoneEditStep(EditStepKind.COPY, uri, newFingerprint)))

        recover()

        assertFalse(uri in phoneWriter.files)
        assertTrue(journal.entries.isEmpty())
    }

    @Test
    fun `recovery waits for the ssd to be plugged in`() = runTest {
        givenInterrupted()
        ssdWriter.available = false

        assertEquals(1, recover())

        assertEquals(1, journal.entries.size)
        assertNull(transfers.records.value[1])
    }

    @Test
    fun `recovery deletes an orphan ssd copy`() = runTest {
        givenBackedUp()
        val copyPath = "DCIM/2025/04/21/IMG_1_edit.jpg"
        journal.write(EditJournalEntry("copy", ssd = SsdEditStep(EditStepKind.COPY, copyPath, newFingerprint)))
        ssdWriter.files["$copyPath.ciao-new"] = rotated

        recover()

        assertEquals(setOf(ssdPath), ssdWriter.files.keys)
    }
}
