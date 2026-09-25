package dev.ybdn.ciao.galerie.domain.util

import dev.ybdn.ciao.galerie.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaFileTypesTest {

    @Test
    fun `recognizes photos and videos by extension, case insensitive`() {
        assertEquals(MediaType.PHOTO, MediaFileTypes.fromFileName("PXL_20260916.JPG")?.mediaType)
        assertEquals(MediaType.VIDEO, MediaFileTypes.fromFileName("VID_20260916.mp4")?.mediaType)
        assertNull(MediaFileTypes.fromFileName("notes.txt"))
    }

    @Test
    fun `ignores macOS AppleDouble sidecar files`() {
        assertNull(MediaFileTypes.fromFileName("._PXL_20260916.jpg"))
        assertNull(MediaFileTypes.fromFileName("._VID_20260916.mp4"))
    }
}
