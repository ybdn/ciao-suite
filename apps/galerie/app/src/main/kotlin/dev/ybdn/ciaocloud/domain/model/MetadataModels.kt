package dev.ybdn.ciaocloud.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Modification d'un champ de métadonnées : conservé, remplacé ou supprimé. */
sealed interface FieldChange<out T> {
    data object Keep : FieldChange<Nothing>
    data class Set<T>(val value: T) : FieldChange<T>
    data object Remove : FieldChange<Nothing>
}

/** Position GPS en degrés décimaux, altitude en mètres (optionnelle). */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
) {
    init {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "Coordonnées invalides" }
        require(altitudeMeters == null || altitudeMeters.isFinite()) { "Altitude invalide" }
    }
}

/** Modifications demandées sur les métadonnées d'une ou plusieurs photos (spec v3 C). */
data class MetadataChanges(
    val description: FieldChange<String> = FieldChange.Keep,
    val artist: FieldChange<String> = FieldChange.Keep,
    val copyright: FieldChange<String> = FieldChange.Keep,
    val location: FieldChange<GeoPoint> = FieldChange.Keep,
    /** Nettoyage confidentialité : position, appareil, identifiants, logiciel (C5). */
    val removeSensitiveData: Boolean = false,
) {
    val isEmpty: Boolean
        get() = description == FieldChange.Keep && artist == FieldChange.Keep && copyright == FieldChange.Keep &&
            location == FieldChange.Keep && !removeSensitiveData
}

/**
 * Presse-papiers de position propre à l'app (« Copier la position » puis « Coller la position »),
 * pratique pour géolocaliser une série de photos. Rien ne sort de l'app.
 */
class LocationClipboard {
    private val _location = MutableStateFlow<GeoPoint?>(null)
    val location: StateFlow<GeoPoint?> = _location.asStateFlow()

    fun copy(point: GeoPoint) {
        _location.value = point
    }
}

/** Bilan d'une modification de métadonnées. */
data class MetadataEditSummary(
    val modified: Int = 0,
    /** Éléments non éditables (format, vidéo…) ignorés. */
    val skipped: Int = 0,
    val failedNames: List<String> = emptyList(),
    /** Droit d'écriture refusé : rien n'a été modifié. */
    val cancelled: Boolean = false,
    val ssdUnavailable: Boolean = false,
)
