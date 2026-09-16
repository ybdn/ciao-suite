package dev.ybdn.ciaocloud.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Iso6709Test {

    @Test
    fun `parses latitude and longitude with optional altitude`() {
        assertEquals(48.8566 to 2.3522, Iso6709.parse("+48.8566+002.3522/"))
        assertEquals(-33.8688 to 151.2093, Iso6709.parse("-33.8688+151.2093+012.5/"))
    }

    @Test
    fun `rejects missing, malformed or out of range values`() {
        assertNull(Iso6709.parse(null))
        assertNull(Iso6709.parse(""))
        assertNull(Iso6709.parse("48.8566,2.3522"))
        assertNull(Iso6709.parse("+98.0000+002.0000/"))
    }
}
