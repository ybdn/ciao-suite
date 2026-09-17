package dev.ybdn.ciaocloud.domain.util

import dev.ybdn.ciaocloud.domain.model.CaptureTimestamp
import dev.ybdn.ciaocloud.domain.model.DateShift
import dev.ybdn.ciaocloud.domain.model.FieldChange
import dev.ybdn.ciaocloud.domain.model.MetadataChanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class CaptureDatesTest {

    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun `a group shift crosses midnight`() {
        val current = CaptureTimestamp(LocalDateTime.of(2025, 4, 21, 23, 30), 120)
        val shifted = CaptureDates.resulting(current, MetadataChanges(dateShift = DateShift(minutes = 45)))

        assertEquals(LocalDateTime.of(2025, 4, 22, 0, 15), shifted.local)
        assertEquals(120, shifted.offsetMinutes)
        assertEquals("DCIM/2025/04/22", CaptureDates.relocation("DCIM/2025/04/21/a.jpg", shifted, paris))
    }

    @Test
    fun `a backwards shift keeps the relative gap between photos`() {
        val shift = MetadataChanges(dateShift = DateShift(days = 1, hours = 2, backwards = true))
        val first = CaptureDates.resulting(CaptureTimestamp(LocalDateTime.of(2025, 4, 21, 10, 0)), shift)
        val second = CaptureDates.resulting(CaptureTimestamp(LocalDateTime.of(2025, 4, 21, 12, 30)), shift)

        assertEquals(LocalDateTime.of(2025, 4, 20, 8, 0), first.local)
        assertEquals(java.time.Duration.ofMinutes(150), java.time.Duration.between(first.local, second.local))
    }

    @Test
    fun `the folder is the local capture day with a negative offset`() {
        // 23 h 30 à New York : 5 h 30 le lendemain à Paris, mais rangée au jour local du lieu.
        val capture = CaptureTimestamp(LocalDateTime.of(2025, 4, 21, 23, 30), -4 * 60)

        assertEquals("DCIM/2025/04/21", CaptureDates.targetDirectory(capture, paris))
        assertEquals(
            LocalDateTime.of(2025, 4, 22, 3, 30).toInstant(ZoneOffset.UTC).toEpochMilli(),
            CaptureDates.epochMillis(capture, paris),
        )
    }

    @Test
    fun `an unknown offset uses the phone time zone`() {
        val capture = CaptureTimestamp(LocalDateTime.of(2025, 4, 21, 0, 30), null)

        assertEquals("DCIM/2025/04/21", CaptureDates.targetDirectory(capture, paris))
        val removed = CaptureDates.resulting(CaptureTimestamp(LocalDateTime.of(2025, 4, 21, 0, 30), 60), MetadataChanges(utcOffsetMinutes = FieldChange.Remove))
        assertNull(removed.offsetMinutes)
    }

    @Test
    fun `the same folder with a different case is not a move`() {
        val capture = CaptureTimestamp(LocalDateTime.of(2025, 4, 21, 10, 0), 120)

        assertNull(CaptureDates.relocation("dcim/2025/04/21/a.jpg", capture, paris))
    }

    @Test
    fun `exif dates and offsets round trip`() {
        val local = LocalDateTime.of(2026, 9, 17, 8, 5, 9)
        assertEquals("2026:09:17 08:05:09", CaptureDates.format(local))
        assertEquals(local, CaptureDates.parse("2026:09:17 08:05:09"))
        assertNull(CaptureDates.parse("2026:13:40 99:00:00"))
        assertEquals("-03:30", CaptureDates.formatOffset(-210))
        assertEquals("+05:45", CaptureDates.formatOffset(345))
        assertEquals(-210, CaptureDates.parseOffset("-03:30"))
        assertEquals(105, CaptureDates.OFFSET_OPTIONS_MINUTES.size)
        assertTrue(0 in CaptureDates.OFFSET_OPTIONS_MINUTES)
    }

    @Test
    fun `plan writes date and offset, or removes an unknown offset`() {
        val plan = ExifWritePlan.from(
            MetadataChanges(
                captureDateTime = FieldChange.Set(LocalDateTime.of(2025, 4, 20, 18, 45)),
                utcOffsetMinutes = FieldChange.Set(330),
            ),
        )
        assertEquals(
            mapOf(
                "DateTimeOriginal" to "2025:04:20 18:45:00",
                "DateTimeDigitized" to "2025:04:20 18:45:00",
                "OffsetTimeOriginal" to "+05:30",
                "OffsetTimeDigitized" to "+05:30",
            ),
            plan.set,
        )
        val unknown = ExifWritePlan.from(MetadataChanges(utcOffsetMinutes = FieldChange.Remove))
        assertEquals(setOf("OffsetTimeOriginal", "OffsetTimeDigitized"), unknown.remove)
        assertTrue(unknown.set.isEmpty())
        // Sous-secondes et horodatage GPS jamais touchés.
        assertTrue(plan.remove.none { it.startsWith("SubSec") || it == "GPSTimeStamp" })
    }
}
