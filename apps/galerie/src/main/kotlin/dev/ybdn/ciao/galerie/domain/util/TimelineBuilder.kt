package dev.ybdn.ciao.galerie.domain.util

import dev.ybdn.ciao.galerie.domain.model.FavoriteKeys
import dev.ybdn.ciao.galerie.domain.model.GalleryFilter
import dev.ybdn.ciao.galerie.domain.model.GalleryItem
import dev.ybdn.ciao.galerie.domain.model.GalleryLocation
import dev.ybdn.ciao.galerie.domain.model.MediaType
import dev.ybdn.ciao.galerie.domain.model.PhoneMedia
import dev.ybdn.ciao.galerie.domain.model.SsdMedia
import dev.ybdn.ciao.galerie.domain.model.TransferRecord
import dev.ybdn.ciao.galerie.domain.model.TimelineDay
import dev.ybdn.ciao.galerie.domain.model.TransferStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fusion pure de la chronologie : médias du téléphone, index du SSD, enregistrements de transfert
 * et favoris. Le lien téléphone ↔ SSD repose uniquement sur l'enregistrement de transfert
 * `VERIFIED` (identifiant MediaStore ↔ chemin de destination), jamais sur le nom de fichier.
 */
object TimelineBuilder {

    private val DAY_PATH = Regex("""DCIM/(\d{4})/(\d{2})/(\d{2})/[^/]+""", RegexOption.IGNORE_CASE)

    fun build(
        phoneMedia: List<PhoneMedia>,
        ssdMedia: List<SsdMedia>,
        transferRecords: List<TransferRecord>,
        favoriteKeys: Set<String>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<GalleryItem> {
        val ssdByPath = ssdMedia.associateBy { it.relativePath.lowercase() }
        val verifiedPathById = transferRecords
            .filter { it.status == TransferStatus.VERIFIED && it.destinationPath != null }
            .associate { it.mediaStoreId to it.destinationPath!!.lowercase() }

        val linkedSsdPaths = HashSet<String>()
        val items = ArrayList<GalleryItem>(phoneMedia.size + ssdMedia.size)

        for (phone in phoneMedia) {
            val ssd = verifiedPathById[phone.mediaStoreId]?.let { ssdByPath[it] }
            if (ssd != null) linkedSsdPaths += ssd.relativePath.lowercase()
            val item = GalleryItem(
                key = FavoriteKeys.phone(phone.mediaStoreId),
                phone = phone,
                ssd = ssd,
                // Rangé sur le SSD : le jour du dossier suit les règles de rangement (fuseau du lieu de prise de vue).
                captureDate = ssd?.captureDate
                    ?: Instant.ofEpochMilli(phone.takenAtEpochMillis).atZone(zoneId).toLocalDate(),
                sortEpochMillis = phone.takenAtEpochMillis,
            )
            items += item.copy(isFavorite = item.favoriteKey in favoriteKeys)
        }

        for (ssd in ssdMedia) {
            if (ssd.relativePath.lowercase() in linkedSsdPaths) continue
            val key = FavoriteKeys.ssd(ssd.relativePath)
            items += GalleryItem(
                key = key,
                phone = null,
                ssd = ssd,
                captureDate = ssd.captureDate,
                sortEpochMillis = ssd.capturedAtEpochMillis,
                isFavorite = key in favoriteKeys,
            )
        }

        items.sortWith(TIMELINE_ORDER)
        return items
    }

    /** Du plus récent au plus ancien ; dans un jour, les instants connus d'abord, puis par nom décroissant. */
    val TIMELINE_ORDER: Comparator<GalleryItem> = compareByDescending<GalleryItem> { it.captureDate }
        .thenByDescending { it.sortEpochMillis != null }
        .thenByDescending { it.sortEpochMillis }
        .thenByDescending { it.displayName }

    fun filter(items: List<GalleryItem>, filter: GalleryFilter): List<GalleryItem> = when (filter) {
        GalleryFilter.ALL -> items
        GalleryFilter.FAVORITES -> items.filter { it.isFavorite }
        GalleryFilter.NOT_BACKED_UP -> items.filter { it.location == GalleryLocation.PHONE }
        GalleryFilter.VIDEOS -> items.filter { it.mediaType == MediaType.VIDEO }
    }

    /** Regroupe des éléments déjà triés par [TIMELINE_ORDER] en jours consécutifs. */
    fun groupByDay(items: List<GalleryItem>): List<TimelineDay> {
        val days = ArrayList<TimelineDay>()
        var start = 0
        while (start < items.size) {
            val date = items[start].captureDate
            var end = start + 1
            while (end < items.size && items[end].captureDate == date) end++
            days += TimelineDay(date, items.subList(start, end))
            start = end
        }
        return days
    }

    /** Jour d'un chemin conforme `DCIM/aaaa/MM/jj/fichier`, null sinon (ou date invalide). */
    fun parseDayFromPath(relativePath: String): LocalDate? {
        val match = DAY_PATH.matchEntire(relativePath) ?: return null
        val (year, month, day) = match.destructured
        return runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
    }
}
