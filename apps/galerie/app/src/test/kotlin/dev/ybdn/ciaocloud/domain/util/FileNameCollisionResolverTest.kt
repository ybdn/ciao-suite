package dev.ybdn.ciaocloud.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameCollisionResolverTest {

    @Test
    fun `returns the desired name when there is no collision`() {
        val result = FileNameCollisionResolver.resolveAvailableName("IMG_0001.jpg") { false }

        assertEquals("IMG_0001.jpg", result)
    }

    @Test
    fun `appends _1 suffix on first collision`() {
        val existing = setOf("IMG_0001.jpg")

        val result = FileNameCollisionResolver.resolveAvailableName("IMG_0001.jpg") { it in existing }

        assertEquals("IMG_0001_1.jpg", result)
    }

    @Test
    fun `increments the suffix until an available name is found`() {
        val existing = setOf("IMG_0001.jpg", "IMG_0001_1.jpg", "IMG_0001_2.jpg")

        val result = FileNameCollisionResolver.resolveAvailableName("IMG_0001.jpg") { it in existing }

        assertEquals("IMG_0001_3.jpg", result)
    }

    @Test
    fun `handles file names without an extension`() {
        val existing = setOf("VIDEO_0001")

        val result = FileNameCollisionResolver.resolveAvailableName("VIDEO_0001") { it in existing }

        assertEquals("VIDEO_0001_1", result)
    }

    @Test
    fun `never overwrites by returning a name that still collides`() {
        val existing = setOf("a.mp4", "a_1.mp4")

        val result = FileNameCollisionResolver.resolveAvailableName("a.mp4") { it in existing }

        assertEquals(false, result in existing)
    }
}
