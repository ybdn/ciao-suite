package dev.ybdn.ciao.galerie.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrippedMetadataCheckTest {

    @Test
    fun `accepts an ultra hdr gain map descriptor`() {
        val xmp = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/" hdrgm:Version="1.0"
                    xmlns:Container="http://ns.google.com/photos/1.0/container/"
                    xmlns:Item="http://ns.google.com/photos/1.0/container/item/">
                  <Container:Directory><rdf:Seq><rdf:li rdf:parseType="Resource">
                    <Container:Item Item:Semantic="Primary" Item:Mime="image/jpeg"/>
                  </rdf:li></rdf:Seq></Container:Directory>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()

        assertEquals(emptySet<String>(), StrippedMetadataCheck.disallowedXmpPrefixes(xmp))
    }

    @Test
    fun `rejects personal xmp properties`() {
        val xmp = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF><rdf:Description
              xmp:CreateDate="2026-09-16T10:10:10" GCamera:MotionPhoto="1">
              <exif:GPSLatitude>48,51.5N</exif:GPSLatitude>
            </rdf:Description></rdf:RDF></x:xmpmeta>
        """.trimIndent()

        assertEquals(setOf("xmp", "GCamera", "exif"), StrippedMetadataCheck.disallowedXmpPrefixes(xmp))
    }

    @Test
    fun `no xmp is acceptable`() {
        assertEquals(emptySet<String>(), StrippedMetadataCheck.disallowedXmpPrefixes(null))
        assertEquals(emptySet<String>(), StrippedMetadataCheck.disallowedXmpPrefixes(""))
    }

    @Test
    fun `a video date is meaningful only after 1970`() {
        assertTrue(StrippedMetadataCheck.isMeaningfulVideoDate("20260916T101010.000Z"))
        assertFalse(StrippedMetadataCheck.isMeaningfulVideoDate("19040101T000000.000Z"))
        assertFalse(StrippedMetadataCheck.isMeaningfulVideoDate("19700101T000000.000Z"))
        assertFalse(StrippedMetadataCheck.isMeaningfulVideoDate(null))
        assertFalse(StrippedMetadataCheck.isMeaningfulVideoDate("garbage"))
    }

    @Test
    fun `checks dates, position, device, serials, software and texts`() {
        listOf(
            "DateTimeOriginal", "OffsetTimeOriginal", "GPSLatitude", "Make", "Model",
            "BodySerialNumber", "Software", "Artist", "Copyright", "ImageDescription", "MakerNote",
        ).forEach { assertTrue(it, it in StrippedMetadataCheck.FORBIDDEN_EXIF_TAGS) }
    }
}
