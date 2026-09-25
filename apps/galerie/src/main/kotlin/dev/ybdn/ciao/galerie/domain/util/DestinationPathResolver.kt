package dev.ybdn.ciao.galerie.domain.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Segments année/mois/jour d'une arborescence de destination, mois et jour toujours sur 2 chiffres. */
data class DateSegments(
    val year: String,
    val month: String,
    val day: String,
) {
    /** Chemin relatif "année/mois/jour", sans slash de tête ni de fin. */
    fun toRelativePath(): String = "$year/$month/$day"
}

/**
 * Logique pure de calcul de l'arborescence de destination sur le SSD :
 * `DCIM/{année}/{mois}/{jour}`, à partir d'un timestamp epoch millis.
 *
 * Aucune dépendance Android : le fuseau horaire est injectable pour les tests.
 */
object DestinationPathResolver {

    const val ROOT_FOLDER = "DCIM"

    /**
     * @param utcOffsetMinutes décalage du lieu de prise de vue s'il est connu : il prime alors sur
     * [zoneId], pour qu'un média pris à l'étranger soit rangé au jour local de la prise de vue.
     */
    fun resolveDateSegments(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        utcOffsetMinutes: Int? = null,
    ): DateSegments {
        val zone: ZoneId = utcOffsetMinutes?.let { ZoneOffset.ofTotalSeconds(it * 60) } ?: zoneId
        val dateTime: ZonedDateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return DateSegments(
            year = dateTime.year.toString(),
            month = dateTime.monthValue.toString().padStart(2, '0'),
            day = dateTime.dayOfMonth.toString().padStart(2, '0'),
        )
    }

    /** Chemin relatif complet "DCIM/{année}/{mois}/{jour}" (sans le nom de fichier). */
    fun resolveDestinationDirectory(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        utcOffsetMinutes: Int? = null,
    ): String {
        val segments = resolveDateSegments(epochMillis, zoneId, utcOffsetMinutes)
        return "$ROOT_FOLDER/${segments.toRelativePath()}"
    }
}
