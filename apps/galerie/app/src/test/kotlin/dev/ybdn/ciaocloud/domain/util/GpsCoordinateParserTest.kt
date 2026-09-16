package dev.ybdn.ciaocloud.domain.util

import dev.ybdn.ciaocloud.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpsCoordinateParserTest {

    private fun assertPoint(expectedLat: Double, expectedLon: Double, actual: GeoPoint?) {
        assertEquals(expectedLat, actual!!.latitude, 1e-4)
        assertEquals(expectedLon, actual.longitude, 1e-4)
    }

    @Test
    fun `decimal degrees`() {
        assertPoint(48.8584, 2.2945, GpsCoordinateParser.parse("48.8584, 2.2945"))
        assertPoint(-33.8688, 151.2093, GpsCoordinateParser.parse(" -33.8688 151.2093 "))
    }

    @Test
    fun `degrees minutes seconds with hemispheres`() {
        assertPoint(48.8583, 2.2944, GpsCoordinateParser.parse("48°51'30\"N 2°17'40\"E"))
        assertPoint(-33.8688, -70.6483, GpsCoordinateParser.parse("33°52'7.7\"S 70°38'54\"W"))
        assertPoint(45.5, -73.5, GpsCoordinateParser.parse("45°30' N, 73°30' O"))
    }

    @Test
    fun `geo uri`() {
        assertPoint(48.8584, 2.2945, GpsCoordinateParser.parse("geo:48.8584,2.2945?q=48.8584,2.2945"))
    }

    @Test
    fun `out of bounds or malformed input is rejected`() {
        assertNull(GpsCoordinateParser.parse("91, 2"))
        assertNull(GpsCoordinateParser.parse("48, 181"))
        assertNull(GpsCoordinateParser.parse("Paris"))
        assertNull(GpsCoordinateParser.parse(""))
        assertNull(GpsCoordinateParser.parse("48°61'0\"N 2°0'0\"E"))
        assertNull(GpsCoordinateParser.parse("48°51'30\"N"))
    }
}
