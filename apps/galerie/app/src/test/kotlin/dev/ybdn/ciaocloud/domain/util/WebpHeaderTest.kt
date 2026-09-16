package dev.ybdn.ciaocloud.domain.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebpHeaderTest {

    private fun chunk(fourCc: String, size: Int) =
        fourCc.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(size.toByte(), (size ushr 8).toByte(), 0, 0) + ByteArray(size + (size and 1))

    private fun riff(vararg chunks: ByteArray) =
        "RIFF".toByteArray() + ByteArray(4) + "WEBP".toByteArray() + chunks.reduce(ByteArray::plus)

    @Test
    fun `simple lossless and lossy files`() {
        assertTrue(WebpHeader.isLossless(riff(chunk("VP8L", 10))))
        assertFalse(WebpHeader.isLossless(riff(chunk("VP8 ", 10))))
    }

    @Test
    fun `extended file skips the icc profile to reach the image chunk`() {
        assertTrue(WebpHeader.isLossless(riff(chunk("VP8X", 10), chunk("ICCP", 301), chunk("VP8L", 4))))
        assertFalse(WebpHeader.isLossless(riff(chunk("VP8X", 10), chunk("ALPH", 7), chunk("VP8 ", 4))))
    }

    @Test
    fun `not a webp`() {
        assertFalse(WebpHeader.isLossless(ByteArray(32)))
        assertFalse(WebpHeader.isLossless(riff(chunk("VP8X", 10))))
    }
}
