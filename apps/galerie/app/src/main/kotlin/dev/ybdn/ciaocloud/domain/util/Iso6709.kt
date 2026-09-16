package dev.ybdn.ciaocloud.domain.util

/** Coordonnées au format ISO 6709 des conteneurs vidéo, ex. "+48.8566+002.3522/" ou "-33.8688+151.2093+012.5/". */
object Iso6709 {

    private val PATTERN = Regex("""^([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""")

    /** (latitude, longitude), ou null si la valeur est absente, mal formée ou hors limites. */
    fun parse(value: String?): Pair<Double, Double>? {
        val match = PATTERN.find(value?.trim() ?: return null) ?: return null
        val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return latitude to longitude
    }
}
