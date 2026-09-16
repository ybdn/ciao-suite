package dev.ybdn.ciaocloud.domain.util

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
}
