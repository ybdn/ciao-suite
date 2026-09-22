package dev.ybdn.ciaocloud.domain.util

import dev.ybdn.ciaocloud.domain.model.MediaFile
import dev.ybdn.ciaocloud.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureOffsetInferrerTest {

    private val hour = 60L * 60 * 1000
    private val base = 1_750_000_000_000L

    private fun media(id: Long, type: MediaType, epochMillis: Long, offset: Int? = null) = MediaFile(
        mediaStoreId = id,
        uri = "content://media/$id",
        displayName = "media_$id",
        mediaType = type,
        mimeType = "application/octet-stream",
        sizeBytes = 1,
        capturedAtEpochMillis = epochMillis,
        fileDateEpochMillis = epochMillis,
        captureUtcOffsetMinutes = offset,
    )

    private fun inferredOffset(video: MediaFile, vararg others: MediaFile): Int? =
        CaptureOffsetInferrer.inferVideoOffsets(listOf(video, *others)).first().captureUtcOffsetMinutes

    @Test
    fun `takes the offset of the closest photo`() {
        val video = media(1, MediaType.VIDEO, base)

        val offset = inferredOffset(
            video,
            media(2, MediaType.PHOTO, base - 3 * hour, offset = 120),
            media(3, MediaType.PHOTO, base + 1 * hour, offset = -240),
        )

        assertEquals(-240, offset)
    }

    @Test
    fun `ignores photos more than 12 hours away`() {
        val video = media(1, MediaType.VIDEO, base)

        val offset = inferredOffset(video, media(2, MediaType.PHOTO, base + 12 * hour + 1, offset = -240))

        assertNull(offset)
    }

    @Test
    fun `prefers the earlier photo on a tie`() {
        val video = media(1, MediaType.VIDEO, base)

        val offset = inferredOffset(
            video,
            media(2, MediaType.PHOTO, base + 2 * hour, offset = -240),
            media(3, MediaType.PHOTO, base - 2 * hour, offset = 120),
        )

        assertEquals(120, offset)
    }

    @Test
    fun `leaves the offset unknown without candidate photos`() {
        val video = media(1, MediaType.VIDEO, base)

        val offset = inferredOffset(
            video,
            media(2, MediaType.PHOTO, base, offset = null),
            media(3, MediaType.VIDEO, base, offset = 60),
        )

        assertNull(offset)
    }

    @Test
    fun `does not change photos or videos with a known offset`() {
        val video = media(1, MediaType.VIDEO, base, offset = 60)
        val photo = media(2, MediaType.PHOTO, base, offset = null)
        val reference = media(3, MediaType.PHOTO, base, offset = -300)

        val result = CaptureOffsetInferrer.inferVideoOffsets(listOf(video, photo, reference))

        assertEquals(listOf(60, null, -300), result.map { it.captureUtcOffsetMinutes })
    }
}
