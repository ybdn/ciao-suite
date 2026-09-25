package dev.ybdn.ciao.galerie.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExifTextTest {

    @Test
    fun `decodes utf-8 and strips the terminating nul`() {
        assertEquals("Été à Nice", ExifText.decode("Été à Nice".toByteArray(Charsets.UTF_8) + byteArrayOf(0)))
    }

    @Test
    fun `falls back to latin-1 for invalid utf-8`() {
        assertEquals("café", ExifText.decode(byteArrayOf(0x63, 0x61, 0x66, 0xE9.toByte(), 0)))
    }

    @Test
    fun `blank text is absent`() {
        assertNull(ExifText.decode(byteArrayOf(0x20, 0)))
        assertNull(ExifText.decode(null))
    }
}
