package dev.ybdn.ciaocloud.domain.util

import dev.ybdn.ciaocloud.domain.model.GalleryFilter
import dev.ybdn.ciaocloud.domain.model.GalleryLocation
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.model.PhoneMedia
import dev.ybdn.ciaocloud.domain.model.SsdMedia
import dev.ybdn.ciaocloud.domain.model.TransferRecord
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TimelineBuilderTest {

    private val paris = ZoneId.of("Europe/Paris")

    private fun phone(id: Long, taken: ZonedDateTime, type: MediaType = MediaType.PHOTO) = PhoneMedia(
        mediaStoreId = id,
        uri = "content://media/$id",
        displayName = "IMG_$id.jpg",
        mediaType = type,
        mimeType = "image/jpeg",
        sizeBytes = 10,
        takenAtEpochMillis = taken.toInstant().toEpochMilli(),
        dateModifiedEpochMillis = 0,
        width = 0,
        height = 0,
        durationMillis = null,
        relativePath = "DCIM/Camera/",
    )

    private fun ssd(path: String, capturedAt: Long? = null) = SsdMedia(
        relativePath = path,
        displayName = path.substringAfterLast('/'),
        mediaType = MediaType.PHOTO,
        mimeType = "image/jpeg",
        sizeBytes = 10,
        captureDate = TimelineBuilder.parseDayFromPath(path)!!,
        capturedAtEpochMillis = capturedAt,
        lastModifiedEpochMillis = 0,
    )

    private fun verified(id: Long, path: String) =
        TransferRecord(id, MediaType.PHOTO, TransferStatus.VERIFIED, destinationPath = path)

    @Test
    fun `links phone and ssd media only through a verified transfer record`() {
        val items = TimelineBuilder.build(
            phoneMedia = listOf(
                phone(1, ZonedDateTime.of(2025, 4, 21, 10, 0, 0, 0, paris)),
                phone(2, ZonedDateTime.of(2025, 4, 21, 11, 0, 0, 0, paris)),
            ),
            ssdMedia = listOf(
                ssd("DCIM/2025/04/21/IMG_1.jpg"),
                // Même nom que le média 2, mais sans enregistrement de transfert : pas de rapprochement.
                ssd("DCIM/2025/04/21/IMG_2.jpg"),
            ),
            transferRecords = listOf(verified(1, "dcim/2025/04/21/img_1.JPG")),
            favoriteKeys = emptySet(),
            zoneId = paris,
        )

        assertEquals(3, items.size)
        assertEquals(GalleryLocation.BOTH, items.single { it.key == "phone:1" }.location)
        assertEquals(GalleryLocation.PHONE, items.single { it.key == "phone:2" }.location)
        assertEquals(GalleryLocation.SSD, items.single { it.key == "ssd:DCIM/2025/04/21/IMG_2.jpg" }.location)
    }

    @Test
    fun `a verified record whose file is not indexed stays phone only`() {
        val items = TimelineBuilder.build(
            phoneMedia = listOf(phone(1, ZonedDateTime.of(2025, 4, 21, 10, 0, 0, 0, paris))),
            ssdMedia = emptyList(),
            transferRecords = listOf(verified(1, "DCIM/2025/04/21/IMG_1.jpg")),
            favoriteKeys = emptySet(),
            zoneId = paris,
        )

        assertEquals(GalleryLocation.PHONE, items.single().location)
    }

    @Test
    fun `a backed up media takes the day of its ssd folder`() {
        // Vidéo tournée le 21 à New York, vue depuis Paris le 22 à 3 h 30 : rangée au 21.
        val items = TimelineBuilder.build(
            phoneMedia = listOf(phone(1, ZonedDateTime.of(2025, 4, 22, 3, 30, 0, 0, paris), MediaType.VIDEO)),
            ssdMedia = listOf(ssd("DCIM/2025/04/21/IMG_1.jpg")),
            transferRecords = listOf(verified(1, "DCIM/2025/04/21/IMG_1.jpg")),
            favoriteKeys = emptySet(),
            zoneId = paris,
        )

        assertEquals(LocalDate.of(2025, 4, 21), items.single().captureDate)
    }

    @Test
    fun `sorts newest first and orders unknown instants by name within a day`() {
        val items = TimelineBuilder.build(
            phoneMedia = listOf(
                phone(1, ZonedDateTime.of(2025, 4, 21, 9, 0, 0, 0, paris)),
                phone(2, ZonedDateTime.of(2025, 4, 22, 9, 0, 0, 0, paris)),
            ),
            ssdMedia = listOf(ssd("DCIM/2025/04/21/A.jpg"), ssd("DCIM/2025/04/21/B.jpg")),
            transferRecords = emptyList(),
            favoriteKeys = emptySet(),
            zoneId = paris,
        )

        assertEquals(
            listOf("phone:2", "phone:1", "ssd:DCIM/2025/04/21/B.jpg", "ssd:DCIM/2025/04/21/A.jpg"),
            items.map { it.key },
        )
    }

    @Test
    fun `favorite key follows the ssd copy once backed up`() {
        val items = TimelineBuilder.build(
            phoneMedia = listOf(phone(1, ZonedDateTime.of(2025, 4, 21, 10, 0, 0, 0, paris))),
            ssdMedia = listOf(ssd("DCIM/2025/04/21/IMG_1.jpg")),
            transferRecords = listOf(verified(1, "DCIM/2025/04/21/IMG_1.jpg")),
            favoriteKeys = setOf("ssd:DCIM/2025/04/21/IMG_1.jpg"),
            zoneId = paris,
        )

        assertEquals(true, items.single().isFavorite)
        assertEquals("ssd:DCIM/2025/04/21/IMG_1.jpg", items.single().favoriteKey)
    }

    @Test
    fun `groups consecutive items by day and filters`() {
        val items = TimelineBuilder.build(
            phoneMedia = listOf(
                phone(1, ZonedDateTime.of(2025, 4, 21, 9, 0, 0, 0, paris)),
                phone(2, ZonedDateTime.of(2025, 4, 21, 18, 0, 0, 0, paris), MediaType.VIDEO),
                phone(3, ZonedDateTime.of(2025, 4, 23, 9, 0, 0, 0, paris)),
            ),
            ssdMedia = emptyList(),
            transferRecords = emptyList(),
            favoriteKeys = emptySet(),
            zoneId = paris,
        )

        val days = TimelineBuilder.groupByDay(items)
        assertEquals(listOf(LocalDate.of(2025, 4, 23), LocalDate.of(2025, 4, 21)), days.map { it.date })
        assertEquals(listOf("phone:2", "phone:1"), days[1].items.map { it.key })
        assertEquals(listOf("phone:2"), TimelineBuilder.filter(items, GalleryFilter.VIDEOS).map { it.key })
    }

    @Test
    fun `parses only strict DCIM day paths`() {
        assertEquals(LocalDate.of(2025, 4, 21), TimelineBuilder.parseDayFromPath("DCIM/2025/04/21/IMG.jpg"))
        assertEquals(LocalDate.of(2025, 4, 21), TimelineBuilder.parseDayFromPath("dcim/2025/04/21/IMG.jpg"))
        assertNull(TimelineBuilder.parseDayFromPath("DCIM/2025/4/21/IMG.jpg"))
        assertNull(TimelineBuilder.parseDayFromPath("DCIM/2025/02/30/IMG.jpg"))
        assertNull(TimelineBuilder.parseDayFromPath("DCIM/2025/04/21/sub/IMG.jpg"))
    }
}
