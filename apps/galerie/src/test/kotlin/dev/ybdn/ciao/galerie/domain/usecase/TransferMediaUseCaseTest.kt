package dev.ybdn.ciao.galerie.domain.usecase

import dev.ybdn.ciao.galerie.domain.model.MediaFile
import dev.ybdn.ciao.galerie.domain.model.MediaType
import dev.ybdn.ciao.galerie.domain.model.TransferProgress
import dev.ybdn.ciao.galerie.domain.model.TransferStatus
import dev.ybdn.ciao.galerie.domain.model.TriageDecision
import dev.ybdn.ciao.galerie.domain.model.TriageState
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TransferMediaUseCaseTest {

    private val writer = FakeDestinationWriter()
    private val transfers = FakeTransferStateRepository()
    private val index = FakeSsdMediaIndex()
    private val favorites = FakeFavoritesRepository(setOf("phone:1"))
    private val triage = FakeTriageRepository(listOf(TriageState("phone:1", TriageDecision.KEPT, 0, null)))
    private val useCase = TransferMediaUseCase(
        writer,
        transfers,
        VerifyTransferUseCase(writer),
        index,
        favorites,
        triage,
        FakeThumbnailCache(),
        DirectTransactionRunner,
    )

    private fun photo(id: Long, name: String, content: ByteArray): MediaFile {
        val uri = "content://media/$id"
        writer.sources[uri] = content
        // Midi UTC avec décalage connu : le jour ne dépend pas du fuseau de la machine de test.
        val taken = ZonedDateTime.of(2025, 4, 21, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        return MediaFile(id, uri, name, MediaType.PHOTO, "image/jpeg", content.size.toLong(), taken, taken, captureUtcOffsetMinutes = 0)
    }

    @Test
    fun `a verified copy is indexed and its favorite and triage decision follow the ssd copy`() = runTest {
        val progress = useCase(listOf(photo(1, "IMG_1.jpg", byteArrayOf(1, 2, 3)))).toList()

        val completed = progress.last() as TransferProgress.BatchCompleted
        assertEquals(1, completed.succeeded)
        assertEquals(TransferStatus.VERIFIED, transfers.records.value[1]!!.status)
        assertEquals("DCIM/2025/04/21/IMG_1.jpg", transfers.records.value[1]!!.destinationPath)
        assertTrue(index.get("DCIM/2025/04/21/IMG_1.jpg") != null)
        assertEquals(setOf("ssd:DCIM/2025/04/21/IMG_1.jpg"), favorites.keys.value)
        assertEquals(TriageDecision.KEPT, triage.states.value["ssd:DCIM/2025/04/21/IMG_1.jpg"]?.decision)
        assertEquals(setOf("ssd:DCIM/2025/04/21/IMG_1.jpg"), triage.states.value.keys)
    }

    @Test
    fun `an identical file already on the ssd is not copied but verified`() = runTest {
        writer.files["DCIM/2025/04/21"] = hashMapOf("img_1.JPG" to byteArrayOf(1, 2, 3))

        val progress = useCase(listOf(photo(1, "IMG_1.jpg", byteArrayOf(1, 2, 3)))).toList()

        val completed = progress.last() as TransferProgress.BatchCompleted
        assertEquals(0, completed.succeeded)
        assertEquals(1, completed.alreadyPresent)
        assertEquals(setOf("img_1.JPG"), writer.files["DCIM/2025/04/21"]!!.keys)
        assertEquals("DCIM/2025/04/21/img_1.JPG", transfers.records.value[1]!!.destinationPath)
        assertEquals(TransferStatus.VERIFIED, transfers.records.value[1]!!.status)
        assertEquals(setOf("ssd:DCIM/2025/04/21/img_1.JPG"), favorites.keys.value)
        assertEquals(setOf("ssd:DCIM/2025/04/21/img_1.JPG"), triage.states.value.keys)
    }

    @Test
    fun `a different file with the same name is copied under a suffixed name`() = runTest {
        writer.files["DCIM/2025/04/21"] = hashMapOf("IMG_1.jpg" to byteArrayOf(9, 9, 9))

        useCase(listOf(photo(1, "IMG_1.jpg", byteArrayOf(1, 2, 3)))).toList()

        assertEquals(setOf("IMG_1.jpg", "IMG_1_1.jpg"), writer.files["DCIM/2025/04/21"]!!.keys)
    }
}
