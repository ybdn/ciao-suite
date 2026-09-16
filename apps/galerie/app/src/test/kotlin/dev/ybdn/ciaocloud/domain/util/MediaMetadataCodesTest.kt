package dev.ybdn.ciaocloud.domain.util

import dev.ybdn.ciaocloud.domain.model.ExposureProgram
import dev.ybdn.ciaocloud.domain.model.HdrFormat
import dev.ybdn.ciaocloud.domain.model.MeteringMode
import dev.ybdn.ciaocloud.domain.model.SceneType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaMetadataCodesTest {

    @Test
    fun `decodes exif enumerations and ignores unknown values`() {
        assertEquals(ExposureProgram.APERTURE_PRIORITY, MediaMetadataCodes.exposureProgram(3))
        assertNull(MediaMetadataCodes.exposureProgram(0))
        assertEquals(MeteringMode.PATTERN, MediaMetadataCodes.meteringMode(5))
        assertNull(MediaMetadataCodes.meteringMode(255))
        assertEquals(SceneType.NIGHT, MediaMetadataCodes.sceneType(3))
        assertNull(MediaMetadataCodes.sceneType(0))
        assertEquals(true, MediaMetadataCodes.whiteBalanceManual(1))
        assertNull(MediaMetadataCodes.whiteBalanceManual(null))
    }

    @Test
    fun `flash fired reads bit 0 unless the device has no flash`() {
        assertEquals(true, MediaMetadataCodes.flashFired(0x19))
        assertEquals(false, MediaMetadataCodes.flashFired(0x10))
        assertEquals(false, MediaMetadataCodes.flashFired(0))
        assertNull(MediaMetadataCodes.flashFired(0x20))
        assertNull(MediaMetadataCodes.flashFired(null))
    }

    @Test
    fun `hdr format from android color transfer`() {
        assertEquals(HdrFormat.HLG, MediaMetadataCodes.hdrFormat(7))
        assertEquals(HdrFormat.HDR10, MediaMetadataCodes.hdrFormat(6))
        assertNull(MediaMetadataCodes.hdrFormat(3))
    }

    @Test
    fun `frame rate from frame count and duration`() {
        assertEquals(30.0, MediaMetadataCodes.frameRate(300, 10_000)!!, 0.001)
        assertNull(MediaMetadataCodes.frameRate(0, 10_000))
        assertNull(MediaMetadataCodes.frameRate(300, null))
        assertNull(MediaMetadataCodes.frameRate(1, 60_000))
    }

    @Test
    fun `readable codec and container names with fallback`() {
        assertEquals("HEVC (H.265)", MediaMetadataCodes.codecName("video/hevc"))
        assertEquals("AAC", MediaMetadataCodes.codecName("audio/mp4a-latm"))
        assertEquals("ALAC", MediaMetadataCodes.codecName("audio/alac"))
        assertEquals("MP4", MediaMetadataCodes.containerName("video/mp4"))
        assertEquals("MSVIDEO", MediaMetadataCodes.containerName("video/x-msvideo"))
    }
}
