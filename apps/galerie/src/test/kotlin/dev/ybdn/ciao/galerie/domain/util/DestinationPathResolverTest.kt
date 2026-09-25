package dev.ybdn.ciao.galerie.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DestinationPathResolverTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `pads single digit month and day with a leading zero`() {
        val epochMillis = ZonedDateTime.of(2025, 4, 21, 14, 30, 22, 0, utc)
            .toInstant()
            .toEpochMilli()

        val segments = DestinationPathResolver.resolveDateSegments(epochMillis, utc)

        assertEquals("2025", segments.year)
        assertEquals("04", segments.month)
        assertEquals("21", segments.day)
    }

    @Test
    fun `keeps two digit month and day unchanged`() {
        val epochMillis = ZonedDateTime.of(2025, 12, 31, 0, 0, 0, 0, utc)
            .toInstant()
            .toEpochMilli()

        val segments = DestinationPathResolver.resolveDateSegments(epochMillis, utc)

        assertEquals("12", segments.month)
        assertEquals("31", segments.day)
    }

    @Test
    fun `builds the full DCIM relative destination directory`() {
        val epochMillis = ZonedDateTime.of(2025, 4, 21, 14, 30, 22, 0, utc)
            .toInstant()
            .toEpochMilli()

        val directory = DestinationPathResolver.resolveDestinationDirectory(epochMillis, utc)

        assertEquals("DCIM/2025/04/21", directory)
    }

    @Test
    fun `resolves the correct calendar day across a timezone boundary`() {
        // 2025-04-21 23:30 UTC == 2025-04-22 01:30 in UTC+2.
        val epochMillis = ZonedDateTime.of(2025, 4, 21, 23, 30, 0, 0, utc)
            .toInstant()
            .toEpochMilli()

        val segmentsInUtcPlus2 = DestinationPathResolver.resolveDateSegments(
            epochMillis,
            ZoneId.of("Europe/Paris"),
        )

        assertEquals("22", segmentsInUtcPlus2.day)
    }

    @Test
    fun `uses the capture offset instead of the phone zone when known`() {
        // Vidéo tournée à New York le 21/04 à 23:30 (UTC-4), triée depuis Paris (03:30 le 22/04).
        val epochMillis = ZonedDateTime.of(2025, 4, 22, 3, 30, 0, 0, utc)
            .toInstant()
            .toEpochMilli()

        val directory = DestinationPathResolver.resolveDestinationDirectory(
            epochMillis,
            ZoneId.of("Europe/Paris"),
            utcOffsetMinutes = -240,
        )

        assertEquals("DCIM/2025/04/21", directory)
    }

    @Test
    fun `applies a positive capture offset across midnight`() {
        // 2025-12-31 20:30 UTC == 2026-01-01 05:30 à Tokyo (UTC+9).
        val epochMillis = ZonedDateTime.of(2025, 12, 31, 20, 30, 0, 0, utc)
            .toInstant()
            .toEpochMilli()

        val directory = DestinationPathResolver.resolveDestinationDirectory(epochMillis, utc, utcOffsetMinutes = 540)

        assertEquals("DCIM/2026/01/01", directory)
    }
}
