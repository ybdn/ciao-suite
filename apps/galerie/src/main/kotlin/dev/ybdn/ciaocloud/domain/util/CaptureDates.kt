package dev.ybdn.ciaocloud.domain.util

import dev.ybdn.ciaocloud.domain.model.CaptureTimestamp
import dev.ybdn.ciaocloud.domain.model.FieldChange
import dev.ybdn.ciaocloud.domain.model.MetadataChanges
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import kotlin.math.abs

/**
 * Dates EXIF (`aaaa:MM:jj HH:mm:ss`, décalage `±HH:mm`) et calcul du dossier jour d'une photo après
 * modification de sa date (spec v3 C2, C6, C7).
 */
object CaptureDates {

    const val DATE_TIME_ORIGINAL = "DateTimeOriginal"
    const val DATE_TIME_DIGITIZED = "DateTimeDigitized"
    const val OFFSET_TIME_ORIGINAL = "OffsetTimeOriginal"
    const val OFFSET_TIME_DIGITIZED = "OffsetTimeDigitized"
    val TAGS = listOf(DATE_TIME_ORIGINAL, OFFSET_TIME_ORIGINAL)

    /** Décalages proposés : −12:00 à +14:00 par quarts d'heure. */
    val OFFSET_OPTIONS_MINUTES: List<Int> = (-12 * 60..14 * 60 step 15).toList()

    private val EXIF_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", Locale.US).withResolverStyle(ResolverStyle.STRICT)

    fun format(local: LocalDateTime): String = EXIF_FORMAT.format(local.withNano(0))

    fun parse(value: String?): LocalDateTime? =
        value?.trim()?.take(19)?.let { runCatching { LocalDateTime.parse(it, EXIF_FORMAT) }.getOrNull() }

    fun formatOffset(minutes: Int): String {
        val sign = if (minutes < 0) "-" else "+"
        return "%s%02d:%02d".format(Locale.US, sign, abs(minutes) / 60, abs(minutes) % 60)
    }

    fun parseOffset(value: String?): Int? =
        value?.trim()?.let { runCatching { ZoneOffset.of(it).totalSeconds / 60 }.getOrNull() }

    fun readFrom(tags: Map<String, String>): CaptureTimestamp =
        CaptureTimestamp(parse(tags[DATE_TIME_ORIGINAL]), parseOffset(tags[OFFSET_TIME_ORIGINAL]))

    /** Date de prise de vue après application de [changes] à [current]. */
    fun resulting(current: CaptureTimestamp, changes: MetadataChanges): CaptureTimestamp {
        val local = when (val change = changes.captureDateTime) {
            is FieldChange.Set -> change.value
            else -> changes.dateShift?.takeUnless { it.isZero }?.let { shift -> current.local?.plus(shift.duration) } ?: current.local
        }
        val offset = when (val change = changes.utcOffsetMinutes) {
            FieldChange.Keep -> current.offsetMinutes
            FieldChange.Remove -> null
            is FieldChange.Set -> change.value
        }
        return CaptureTimestamp(local, offset)
    }

    /** Instant de la prise de vue : heure locale moins le décalage, à défaut dans le fuseau du téléphone. */
    fun epochMillis(capture: CaptureTimestamp, zoneId: ZoneId): Long? {
        val local = capture.local ?: return null
        val offset = capture.offsetMinutes
        return if (offset != null) {
            local.toInstant(ZoneOffset.ofTotalSeconds(offset * 60)).toEpochMilli()
        } else {
            local.atZone(zoneId).toInstant().toEpochMilli()
        }
    }

    /** Dossier `DCIM/aaaa/MM/jj` de la photo (calcul du transfert, décalage saisi s'il est connu). */
    fun targetDirectory(capture: CaptureTimestamp, zoneId: ZoneId): String? {
        val epoch = epochMillis(capture, zoneId) ?: return null
        return DestinationPathResolver.resolveDestinationDirectory(epoch, zoneId, capture.offsetMinutes)
    }

    /**
     * Nouveau dossier jour d'un fichier du SSD, ou null s'il reste où il est (comparaison insensible à
     * la casse : `dcim/2025/04/20` et `DCIM/2025/04/20` sont le même dossier).
     */
    fun relocation(relativePath: String, capture: CaptureTimestamp, zoneId: ZoneId): String? {
        val target = targetDirectory(capture, zoneId) ?: return null
        val current = relativePath.substringBeforeLast('/')
        return target.takeUnless { it.equals(current, ignoreCase = true) }
    }
}
