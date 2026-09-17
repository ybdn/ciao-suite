package dev.ybdn.ciaocloud.domain.util

import dev.ybdn.ciaocloud.domain.model.FieldChange
import dev.ybdn.ciaocloud.domain.model.GeoPoint
import dev.ybdn.ciaocloud.domain.model.MetadataChanges
import kotlin.math.abs
import kotlin.math.roundToLong

/** Noms des balises EXIF (identiques aux constantes `ExifInterface`), sans dépendance Android. */
object ExifTags {
    const val ORIENTATION = "Orientation"
    const val IMAGE_DESCRIPTION = "ImageDescription"
    const val ARTIST = "Artist"
    const val COPYRIGHT = "Copyright"

    const val GPS_LATITUDE = "GPSLatitude"
    const val GPS_LATITUDE_REF = "GPSLatitudeRef"
    const val GPS_LONGITUDE = "GPSLongitude"
    const val GPS_LONGITUDE_REF = "GPSLongitudeRef"
    const val GPS_ALTITUDE = "GPSAltitude"
    const val GPS_ALTITUDE_REF = "GPSAltitudeRef"

    /** Balises de texte libre, encodées en UTF-8 par l'app. */
    val TEXT = setOf(IMAGE_DESCRIPTION, ARTIST, COPYRIGHT, "Make", "Model", "LensMake", "LensModel", "Software")

    /** Toutes les balises GPS connues d'`ExifInterface`. */
    val GPS = listOf(
        "GPSVersionID", GPS_LATITUDE_REF, GPS_LATITUDE, GPS_LONGITUDE_REF, GPS_LONGITUDE, GPS_ALTITUDE_REF,
        GPS_ALTITUDE, "GPSTimeStamp", "GPSSatellites", "GPSStatus", "GPSMeasureMode", "GPSDOP", "GPSSpeedRef",
        "GPSSpeed", "GPSTrackRef", "GPSTrack", "GPSImgDirectionRef", "GPSImgDirection", "GPSMapDatum",
        "GPSDestLatitudeRef", "GPSDestLatitude", "GPSDestLongitudeRef", "GPSDestLongitude", "GPSDestBearingRef",
        "GPSDestBearing", "GPSDestDistanceRef", "GPSDestDistance", "GPSProcessingMethod", "GPSAreaInformation",
        "GPSDateStamp", "GPSDifferential", "GPSHPositioningError",
    )

    /** Appareil, identifiants et logiciel retirés par le nettoyage confidentialité (C5). */
    val SENSITIVE_DEVICE = listOf(
        "Make", "Model", "LensMake", "LensModel",
        "BodySerialNumber", "LensSerialNumber", "CameraOwnerName", "ImageUniqueID",
        "Software", "MakerNote",
    )
}

/**
 * Balises à écrire et à supprimer dans un fichier, sans toucher aux pixels ni aux autres balises.
 * Une balise ne peut pas être à la fois écrite et supprimée.
 */
data class ExifWritePlan(
    val set: Map<String, String> = emptyMap(),
    val remove: Set<String> = emptySet(),
) {
    init {
        require(set.keys.none { it in remove }) { "Balise à la fois écrite et supprimée" }
    }

    val isEmpty: Boolean get() = set.isEmpty() && remove.isEmpty()

    companion object {
        const val MAX_TEXT_LENGTH = 2000

        fun orientation(value: Int): ExifWritePlan {
            require(value in 1..8) { "Orientation invalide : $value" }
            return ExifWritePlan(set = mapOf(ExifTags.ORIENTATION to value.toString()))
        }

        /**
         * Balises correspondant à [changes]. Une valeur écrite l'emporte sur un retrait (ex. nettoyage
         * puis nouvelle position). Un texte vide supprime sa balise.
         */
        fun from(changes: MetadataChanges): ExifWritePlan {
            val set = LinkedHashMap<String, String>()
            val remove = LinkedHashSet<String>()

            fun text(tag: String, change: FieldChange<String>) {
                when (change) {
                    FieldChange.Keep -> Unit
                    FieldChange.Remove -> remove += tag
                    is FieldChange.Set -> {
                        val value = change.value.trim()
                        require(value.length <= MAX_TEXT_LENGTH) { "Texte trop long" }
                        if (value.isEmpty()) remove += tag else set[tag] = value
                    }
                }
            }
            text(ExifTags.IMAGE_DESCRIPTION, changes.description)
            text(ExifTags.ARTIST, changes.artist)
            text(ExifTags.COPYRIGHT, changes.copyright)

            if (changes.removeSensitiveData) {
                remove += ExifTags.GPS
                remove += ExifTags.SENSITIVE_DEVICE
            }
            when (val location = changes.location) {
                FieldChange.Keep -> Unit
                FieldChange.Remove -> remove += ExifTags.GPS
                is FieldChange.Set -> {
                    // Direction, horodatage… deviennent incohérents avec une nouvelle position.
                    remove += ExifTags.GPS
                    set += locationTags(location.value)
                }
            }
            return ExifWritePlan(set, remove - set.keys)
        }

        private fun locationTags(point: GeoPoint): Map<String, String> = buildMap {
            put(ExifTags.GPS_LATITUDE, toDmsRational(point.latitude))
            put(ExifTags.GPS_LATITUDE_REF, if (point.latitude < 0) "S" else "N")
            put(ExifTags.GPS_LONGITUDE, toDmsRational(point.longitude))
            put(ExifTags.GPS_LONGITUDE_REF, if (point.longitude < 0) "W" else "E")
            point.altitudeMeters?.let { altitude ->
                put(ExifTags.GPS_ALTITUDE, "${(abs(altitude) * 100).roundToLong()}/100")
                // 0 : au-dessus du niveau de la mer, 1 : en dessous.
                put(ExifTags.GPS_ALTITUDE_REF, if (altitude < 0) "1" else "0")
            }
        }

        /** Degrés décimaux → rationnels EXIF « d/1,m/1,s×10000/10000 » (valeur absolue). */
        fun toDmsRational(decimalDegrees: Double): String {
            val totalSeconds = (abs(decimalDegrees) * 3600 * 10_000).roundToLong()
            val degrees = totalSeconds / (3600 * 10_000)
            val minutes = totalSeconds % (3600 * 10_000) / (60 * 10_000)
            val seconds = totalSeconds % (60 * 10_000)
            return "$degrees/1,$minutes/1,$seconds/10000"
        }
    }
}
