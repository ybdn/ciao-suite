package dev.ybdn.ciaocloud.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class EditedFileNamerTest {

    @Test
    fun `adds the edit suffix`() {
        assertEquals("PXL_1_edit.jpg", EditedFileNamer.copyName("PXL_1.jpg", null, emptyList()))
    }

    @Test
    fun `numbers the copy on collision ignoring case`() {
        assertEquals("PXL_1_edit_2.jpg", EditedFileNamer.copyName("PXL_1.jpg", null, listOf("pxl_1_EDIT.JPG", "PXL_1_edit_1.jpg")))
    }

    @Test
    fun `changes the extension of a converted copy`() {
        assertEquals("IMG_1_edit.jpg", EditedFileNamer.copyName("IMG_1.HEIC", "jpg", listOf("IMG_1_edit.heic")))
    }

    @Test
    fun `name without extension`() {
        assertEquals("photo_edit", EditedFileNamer.copyName("photo", null, emptyList()))
    }
}
