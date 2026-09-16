package dev.ybdn.ciaocloud.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataStripPlanTest {

    @Test
    fun `maps each supported mime type to its strategy`() {
        mapOf(
            "image/jpeg" to StripStrategy.REENCODE_JPEG,
            "image/png" to StripStrategy.REENCODE_PNG,
            "image/webp" to StripStrategy.REENCODE_WEBP,
            "image/heic" to StripStrategy.CONVERT_TO_JPEG,
            "image/heif" to StripStrategy.CONVERT_TO_JPEG,
            "image/avif" to StripStrategy.CONVERT_TO_JPEG,
            "image/x-adobe-dng" to StripStrategy.CONVERT_TO_JPEG,
            "video/mp4" to StripStrategy.REMUX_VIDEO,
            "video/quicktime" to StripStrategy.REMUX_VIDEO,
            "video/3gpp" to StripStrategy.REMUX_VIDEO,
            "video/webm" to StripStrategy.REMUX_VIDEO,
            "image/gif" to StripStrategy.UNSUPPORTED,
            "video/x-matroska" to StripStrategy.UNSUPPORTED,
            "application/pdf" to StripStrategy.UNSUPPORTED,
        ).forEach { (mime, strategy) -> assertEquals(mime, strategy, MetadataStripPlan.strategyFor(mime)) }
    }

    @Test
    fun `ignores mime type case and parameters`() {
        assertEquals(StripStrategy.REENCODE_JPEG, MetadataStripPlan.strategyFor(" IMAGE/JPEG; charset=binary"))
        assertEquals(StripStrategy.REMUX_VIDEO, MetadataStripPlan.strategyFor("Video/QuickTime"))
    }

    @Test
    fun `keeps the name of a reencoded photo`() {
        assertEquals(
            StripTarget(StripStrategy.REENCODE_JPEG, "PXL_1.JPEG", "image/jpeg"),
            MetadataStripPlan.targetFor("PXL_1.JPEG", "image/jpeg"),
        )
        assertEquals("a.png", MetadataStripPlan.targetFor("a.png", "image/png").fileName)
        assertEquals("a.webp", MetadataStripPlan.targetFor("a.webp", "image/webp").fileName)
    }

    @Test
    fun `converted photos get a jpg extension`() {
        assertEquals(
            StripTarget(StripStrategy.CONVERT_TO_JPEG, "IMG_1.jpg", "image/jpeg"),
            MetadataStripPlan.targetFor("IMG_1.HEIC", "image/heic"),
        )
        assertEquals("raw.jpg", MetadataStripPlan.targetFor("raw.dng", "image/x-adobe-dng").fileName)
    }

    @Test
    fun `mov videos are remuxed into mp4`() {
        assertEquals(
            StripTarget(StripStrategy.REMUX_VIDEO, "clip.mp4", "video/mp4", VideoContainer.MP4),
            MetadataStripPlan.targetFor("clip.MOV", "video/quicktime"),
        )
        assertEquals(
            StripTarget(StripStrategy.REMUX_VIDEO, "clip.3gp", "video/3gpp", VideoContainer.THREE_GPP),
            MetadataStripPlan.targetFor("clip.3gp", "video/3gpp"),
        )
        assertEquals(VideoContainer.WEBM, MetadataStripPlan.targetFor("clip.webm", "video/webm").videoContainer)
    }

    @Test
    fun `adds an extension to a name without one`() {
        assertEquals("photo.jpg", MetadataStripPlan.targetFor("photo", "image/jpeg").fileName)
    }

    @Test
    fun `falls back to the file extension for a generic mime type`() {
        assertEquals(StripStrategy.REENCODE_JPEG, MetadataStripPlan.targetFor("a.jpg", "application/octet-stream").strategy)
        assertEquals(StripStrategy.REMUX_VIDEO, MetadataStripPlan.targetFor("a.mp4", "*/*").strategy)
        assertEquals(StripStrategy.UNSUPPORTED, MetadataStripPlan.targetFor("a.bin", "application/octet-stream").strategy)
    }

    @Test
    fun `a gif stays unsupported whatever its name`() {
        assertEquals(StripStrategy.UNSUPPORTED, MetadataStripPlan.targetFor("anim.jpg", "image/gif").strategy)
    }

    @Test
    fun `estimates the required space with a margin`() {
        assertEquals(1200L, MetadataStripPlan.estimateRequiredBytes(listOf(400, 600)))
        assertEquals(2L, MetadataStripPlan.estimateRequiredBytes(listOf(1)))
        assertEquals(0L, MetadataStripPlan.estimateRequiredBytes(emptyList()))
    }
}
