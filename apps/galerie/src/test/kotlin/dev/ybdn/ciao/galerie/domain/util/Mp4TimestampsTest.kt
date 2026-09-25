package dev.ybdn.ciao.galerie.domain.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class Mp4TimestampsTest {

    private class Bytes(val data: ByteArray) : RandomAccessBytes {
        override val length: Long get() = data.size.toLong()
        override fun read(position: Long, buffer: ByteArray): Int {
            val count = minOf(buffer.size.toLong(), length - position).toInt().coerceAtLeast(0)
            data.copyInto(buffer, 0, position.toInt(), position.toInt() + count)
            return count
        }
        override fun write(position: Long, bytes: ByteArray) {
            bytes.copyInto(data, position.toInt())
        }
    }

    private fun box(type: String, payload: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(int32(8 + payload.size))
        write(type.toByteArray(Charsets.ISO_8859_1))
        write(payload)
    }.toByteArray()

    private fun int32(value: Int) = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

    private val time32 = byteArrayOf(0x7F, 0x7F, 0x7F, 0x7F)
    private val time64 = ByteArray(8) { 0x7F }

    /** Full box v0 : version/drapeaux, création, modification, puis un champ à préserver. */
    private fun timedV0(type: String) = box(type, byteArrayOf(0, 0, 0, 0) + time32 + time32 + byteArrayOf(9, 9, 9, 9))

    @Test
    fun `erases creation and modification times of movie, track and media headers`() {
        val mvhd = timedV0("mvhd")
        val mdhdV1 = box("mdhd", byteArrayOf(1, 0, 0, 0) + time64 + time64 + byteArrayOf(9, 9))
        val trak = box("trak", timedV0("tkhd") + box("mdia", mdhdV1))
        val mdat = box("mdat", ByteArray(32) { 5 })
        val file = Bytes(box("ftyp", ByteArray(8)) + mdat + box("moov", mvhd + trak))

        assertEquals(3, Mp4Timestamps.erase(file))

        val expectedMvhd = box("mvhd", ByteArray(12) + byteArrayOf(9, 9, 9, 9))
        val expectedMdhd = box("mdhd", byteArrayOf(1, 0, 0, 0) + ByteArray(16) + byteArrayOf(9, 9))
        val expected = box("ftyp", ByteArray(8)) + mdat +
            box("moov", expectedMvhd + box("trak", box("tkhd", ByteArray(12) + byteArrayOf(9, 9, 9, 9)) + box("mdia", expectedMdhd)))
        assertArrayEquals(expected, file.data)
    }

    @Test
    fun `leaves a file without movie box untouched`() {
        val original = box("ftyp", ByteArray(8)) + box("mdat", ByteArray(16) { 3 })
        val file = Bytes(original.copyOf())

        assertEquals(0, Mp4Timestamps.erase(file))
        assertArrayEquals(original, file.data)
    }

    @Test
    fun `stops on a corrupted box size`() {
        val corrupted = int32(1000) + "moov".toByteArray() + ByteArray(8)

        assertEquals(0, Mp4Timestamps.erase(Bytes(corrupted)))
    }
}
