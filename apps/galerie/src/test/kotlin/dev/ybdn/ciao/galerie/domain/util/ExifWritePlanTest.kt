package dev.ybdn.ciao.galerie.domain.util

import dev.ybdn.ciao.galerie.domain.model.FieldChange
import dev.ybdn.ciao.galerie.domain.model.GeoPoint
import dev.ybdn.ciao.galerie.domain.model.MetadataChanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifWritePlanTest {

    @Test
    fun `texts are trimmed and an empty text removes its tag`() {
        val plan = ExifWritePlan.from(
            MetadataChanges(
                description = FieldChange.Set("  Été à Nice "),
                artist = FieldChange.Set(""),
                copyright = FieldChange.Remove,
            ),
        )

        assertEquals(mapOf("ImageDescription" to "Été à Nice"), plan.set)
        assertEquals(setOf("Artist", "Copyright"), plan.remove)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `texts are limited to 2000 characters`() {
        ExifWritePlan.from(MetadataChanges(description = FieldChange.Set("a".repeat(2001))))
    }

    @Test
    fun `a new position removes direction and timestamp`() {
        val plan = ExifWritePlan.from(MetadataChanges(location = FieldChange.Set(GeoPoint(-33.8688, 151.2093, -12.5))))

        assertEquals(
            mapOf(
                "GPSLatitude" to "33/1,52/1,76800/10000",
                "GPSLatitudeRef" to "S",
                "GPSLongitude" to "151/1,12/1,334800/10000",
                "GPSLongitudeRef" to "E",
                "GPSAltitude" to "1250/100",
                "GPSAltitudeRef" to "1",
            ),
            plan.set,
        )
        assertTrue("GPSImgDirection" in plan.remove)
        assertTrue("GPSTimeStamp" in plan.remove)
        assertTrue(plan.remove.none { it in plan.set })
    }

    @Test
    fun `a position without altitude removes the previous altitude`() {
        val plan = ExifWritePlan.from(MetadataChanges(location = FieldChange.Set(GeoPoint(48.8584, -2.2945))))

        assertEquals("W", plan.set["GPSLongitudeRef"])
        assertTrue("GPSAltitude" in plan.remove)
    }

    @Test
    fun `removing the position removes every gps tag`() {
        val plan = ExifWritePlan.from(MetadataChanges(location = FieldChange.Remove))

        assertEquals(ExifTags.GPS.toSet(), plan.remove)
        assertTrue(plan.set.isEmpty())
    }

    @Test
    fun `privacy cleanup keeps dates, shooting parameters and texts`() {
        val plan = ExifWritePlan.from(MetadataChanges(removeSensitiveData = true))

        listOf(
            "GPSLatitude", "Make", "Model", "LensMake", "LensModel", "BodySerialNumber", "LensSerialNumber",
            "CameraOwnerName", "ImageUniqueID", "Software", "MakerNote",
        ).forEach { assertTrue(it, it in plan.remove) }
        listOf("DateTimeOriginal", "OffsetTimeOriginal", "FNumber", "ImageDescription", "Artist", "Xmp")
            .forEach { assertTrue(it, it !in plan.remove) }
    }

    @Test
    fun `a position pasted after cleanup is written`() {
        val plan = ExifWritePlan.from(MetadataChanges(location = FieldChange.Set(GeoPoint(1.0, 2.0)), removeSensitiveData = true))

        assertEquals("N", plan.set["GPSLatitudeRef"])
        assertTrue("Make" in plan.remove)
        assertTrue("GPSLatitude" !in plan.remove)
    }

    @Test
    fun `decimal degrees to rationals`() {
        assertEquals("48/1,51/1,302400/10000", ExifWritePlan.toDmsRational(48.8584))
        assertEquals("0/1,0/1,0/10000", ExifWritePlan.toDmsRational(0.0))
    }
}
