package dev.ybdn.ciao.galerie.domain.usecase

import dev.ybdn.ciao.galerie.domain.model.GalleryItem
import dev.ybdn.ciao.galerie.domain.model.MediaType
import dev.ybdn.ciao.galerie.domain.model.PhoneMedia
import dev.ybdn.ciao.galerie.domain.model.SsdMedia
import dev.ybdn.ciao.galerie.domain.repository.ShareableMedia
import dev.ybdn.ciao.galerie.domain.repository.ShareableMediaProvider
import dev.ybdn.ciao.galerie.domain.repository.SharePreferences
import dev.ybdn.ciao.galerie.domain.repository.StripFailureReason
import dev.ybdn.ciao.galerie.domain.repository.StripOutcome
import dev.ybdn.ciao.galerie.domain.util.StripTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PrepareShareUseCaseTest {

    private class FakeSharePreferences(enabled: Boolean) : SharePreferences {
        val value = MutableStateFlow(enabled)
        override fun observeStripMetadata(): Flow<Boolean> = value
        override suspend fun setStripMetadata(enabled: Boolean) {
            value.value = enabled
        }
    }

    private class FakeShareProvider : ShareableMediaProvider {
        var available: Long? = null
        var failing = setOf<String>()
        val batches = mutableListOf<String>()
        val deletedBatches = mutableListOf<String>()
        val copiedSources = mutableListOf<String>()
        val strippedSources = mutableListOf<String>()
        var beforeStrip: suspend () -> Unit = {}

        override suspend fun availableBytes() = available
        override suspend fun createBatch() = "batch${batches.size}".also { batches += it }
        override suspend fun copy(batchId: String, index: Int, sourceUri: String, fileName: String, mimeType: String): ShareableMedia {
            copiedSources += sourceUri
            return ShareableMedia("content://provider/$batchId/$index/$fileName", mimeType)
        }
        override suspend fun copyWithoutMetadata(batchId: String, index: Int, sourceUri: String, target: StripTarget): StripOutcome {
            beforeStrip()
            strippedSources += sourceUri
            if (sourceUri in failing) return StripOutcome.Failure(StripFailureReason.DECODE_FAILED)
            return StripOutcome.Success(ShareableMedia("content://provider/$batchId/$index/${target.fileName}", target.mimeType))
        }
        override suspend fun deleteBatch(batchId: String) {
            deletedBatches += batchId
        }
        override suspend fun clearTemporaryCopies() = Unit
    }

    private val provider = FakeShareProvider()
    private val browser = FakeSsdMediaBrowser()
    private val day = LocalDate.of(2026, 9, 16)

    private fun phoneItem(id: Long, name: String = "PXL_$id.jpg", mime: String = "image/jpeg", size: Long = 100) = GalleryItem(
        "phone:$id",
        PhoneMedia(id, "content://media/external/images/media/$id", name, MediaType.PHOTO, mime, size, 0, 0, 0, 0, null, "DCIM/Camera/"),
        null,
        day,
        0,
    )

    private fun ssdItem(name: String) = GalleryItem(
        "ssd:DCIM/2026/09/16/$name",
        null,
        SsdMedia("DCIM/2026/09/16/$name", name, MediaType.VIDEO, "video/mp4", 100, day, null, 0),
        day,
        null,
    )

    private fun useCase(strip: Boolean) = PrepareShareUseCase(FakeSharePreferences(strip), provider, browser)

    @Test
    fun `setting disabled shares phone media by their mediastore uri`() = runTest {
        val outcome = useCase(strip = false)(listOf(phoneItem(1), ssdItem("VID.mp4")))

        assertEquals(
            ShareOutcome.Ready(
                listOf(
                    ShareableMedia("content://media/external/images/media/1", "image/jpeg"),
                    ShareableMedia("content://provider/batch0/1/VID.mp4", "video/mp4"),
                ),
            ),
            outcome,
        )
        assertEquals(listOf("content://ssd/DCIM/2026/09/16/VID.mp4"), provider.copiedSources)
        assertEquals(emptyList<String>(), provider.strippedSources)
    }

    @Test
    fun `setting enabled never hands over an original uri`() = runTest {
        val progress = mutableListOf<Pair<Int, Int>>()

        val outcome = useCase(strip = true)(listOf(phoneItem(1, "IMG.HEIC", "image/heic"), ssdItem("VID.mov"))) { current, total ->
            progress += current to total
        }

        assertEquals(
            ShareOutcome.Ready(
                listOf(
                    ShareableMedia("content://provider/batch0/0/IMG.jpg", "image/jpeg"),
                    ShareableMedia("content://provider/batch0/1/VID.mp4", "video/mp4"),
                ),
            ),
            outcome,
        )
        assertEquals(emptyList<String>(), provider.copiedSources)
        assertEquals(listOf(1 to 2, 2 to 2), progress)
    }

    @Test
    fun `partial failure keeps the cleaned copies and names the failed media`() = runTest {
        provider.failing = setOf("content://media/external/images/media/2")

        val outcome = useCase(strip = true)(listOf(phoneItem(1), phoneItem(2), phoneItem(3, "anim.gif", "image/gif")))

        assertEquals(
            ShareOutcome.PartiallyFailed(
                media = listOf(ShareableMedia("content://provider/batch0/0/PXL_1.jpg", "image/jpeg")),
                failedNames = listOf("PXL_2.jpg", "anim.gif"),
                batchId = "batch0",
            ),
            outcome,
        )
        // Un format non pris en charge n'est même pas soumis au nettoyage.
        assertEquals(2, provider.strippedSources.size)
    }

    @Test
    fun `discarding a partial share deletes its copies`() = runTest {
        provider.failing = setOf("content://media/external/images/media/1")
        val useCase = useCase(strip = true)

        val outcome = useCase(listOf(phoneItem(1))) as ShareOutcome.PartiallyFailed
        useCase.discard(outcome)

        assertEquals(emptyList<ShareableMedia>(), outcome.media)
        assertEquals(listOf("batch0"), provider.deletedBatches)
    }

    @Test
    fun `insufficient space stops before any copy`() = runTest {
        provider.available = 239

        val outcome = useCase(strip = true)(listOf(phoneItem(1, size = 100), phoneItem(2, size = 100)))

        assertEquals(ShareOutcome.InsufficientSpace(240), outcome)
        assertEquals(emptyList<String>(), provider.batches)
    }

    @Test
    fun `unplugged ssd aborts before any copy`() = runTest {
        val unplugged = PrepareShareUseCase(FakeSharePreferences(true), provider, FakeSsdMediaBrowser(available = false))

        assertEquals(ShareOutcome.SsdUnavailable, unplugged(listOf(phoneItem(1), ssdItem("VID.mp4"))))
        assertEquals(emptyList<String>(), provider.batches)
    }

    @Test
    fun `cancelling deletes the copies already produced`() = runTest {
        val started = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        var calls = 0
        provider.beforeStrip = {
            if (++calls == 2) {
                started.complete(Unit)
                never.await()
            }
        }

        val job = launch { useCase(strip = true)(listOf(phoneItem(1), phoneItem(2))) }
        started.await()
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(listOf("batch0"), provider.deletedBatches)
    }
}
