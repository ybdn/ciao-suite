package dev.ybdn.ciao.galerie.domain.util

import dev.ybdn.ciao.galerie.domain.model.GeoPoint

/**
 * Saisie d'une position : décimal (`48.8584, 2.2945`), degrés-minutes-secondes
 * (`48°51'30"N 2°17'40"E`) ou URI `geo:48.8584,2.2945`. Aucun géocodage, aucun réseau.
 */
object GpsCoordinateParser {

    private val GEO_URI = Regex("""^geo:\s*([+-]?\d+(?:[.,]\d+)?)\s*,\s*([+-]?\d+(?:[.,]\d+)?)(?:\s*,\s*([+-]?\d+(?:[.,]\d+)?))?.*$""", RegexOption.IGNORE_CASE)
    private val DECIMAL = Regex("""^([+-]?\d+(?:\.\d+)?)\s*[,;\s]\s*([+-]?\d+(?:\.\d+)?)$""")
    private val DMS_PART = Regex(
        """(\d+(?:[.,]\d+)?)\s*[°º]\s*(?:(\d+(?:[.,]\d+)?)\s*['′’]\s*)?(?:(\d+(?:[.,]\d+)?)\s*(?:["″”]|''|′′)\s*)?([NSEWOnsewo])""",
    )

    /** @return la position, ou null si le texte n'est pas reconnu ou hors bornes (« Coordonnées invalides »). */
    fun parse(text: String): GeoPoint? {
        val input = text.trim()
        if (input.isEmpty()) return null
        GEO_URI.matchEntire(input)?.let { match ->
            return point(match.groupValues[1].toCoordinate(), match.groupValues[2].toCoordinate())
        }
        DECIMAL.matchEntire(input)?.let { match ->
            return point(match.groupValues[1].toDoubleOrNull(), match.groupValues[2].toDoubleOrNull())
        }
        return parseDms(input)
    }

    private fun parseDms(input: String): GeoPoint? {
        val parts = DMS_PART.findAll(input).toList()
        if (parts.size != 2) return null
        var latitude: Double? = null
        var longitude: Double? = null
        for (part in parts) {
            val (degrees, minutes, seconds, hemisphere) = part.destructured
            val minutesValue = minutes.ifEmpty { "0" }.toCoordinate() ?: return null
            val secondsValue = seconds.ifEmpty { "0" }.toCoordinate() ?: return null
            if (minutesValue >= 60 || secondsValue >= 60) return null
            val absolute = (degrees.toCoordinate() ?: return null) + minutesValue / 60 + secondsValue / 3600
            when (hemisphere.uppercase()) {
                "N" -> latitude = absolute
                "S" -> latitude = -absolute
                "E" -> longitude = absolute
                // « O » : ouest en français.
                "W", "O" -> longitude = -absolute
            }
        }
        return point(latitude, longitude)
    }

    private fun point(latitude: Double?, longitude: Double?): GeoPoint? {
        if (latitude == null || longitude == null) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return GeoPoint(latitude, longitude)
    }

    private fun String.toCoordinate(): Double? = replace(',', '.').toDoubleOrNull()
}
