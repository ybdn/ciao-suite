package dev.ybdn.ciaocloud.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Duration
import java.time.LocalDateTime
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

/** Décalage groupé de la date de prise de vue (correction d'un appareil mal réglé). */
data class DateShift(
    val days: Int = 0,
    val hours: Int = 0,
    val minutes: Int = 0,
    /** true : vers le passé. */
    val backwards: Boolean = false,
) {
    val duration: Duration
        get() = Duration.ofDays(days.toLong()).plusHours(hours.toLong()).plusMinutes(minutes.toLong())
            .let { if (backwards) it.negated() else it }

    val isZero: Boolean get() = duration.isZero
}

/** Date de prise de vue telle qu'écrite dans l'EXIF : heure locale et décalage UTC (null si inconnus). */
data class CaptureTimestamp(
    val local: LocalDateTime? = null,
    val offsetMinutes: Int? = null,
)

/** Modifications demandées sur les métadonnées d'une ou plusieurs photos (spec v3 C). */
data class MetadataChanges(
    val description: FieldChange<String> = FieldChange.Keep,
    val artist: FieldChange<String> = FieldChange.Keep,
    val copyright: FieldChange<String> = FieldChange.Keep,
    val location: FieldChange<GeoPoint> = FieldChange.Keep,
    /** Nettoyage confidentialité : position, appareil, identifiants, logiciel (C5). */
    val removeSensitiveData: Boolean = false,
    /** Nouvelle date et heure locales de prise de vue (C2). */
    val captureDateTime: FieldChange<LocalDateTime> = FieldChange.Keep,
    /** Décalage UTC en minutes ; `Remove` = « Inconnu ». Seul, il fixe le fuseau sans changer l'heure locale. */
    val utcOffsetMinutes: FieldChange<Int> = FieldChange.Keep,
    /** Décalage groupé appliqué à la date de chaque photo (C6). */
    val dateShift: DateShift? = null,
) {
    /** La date, l'heure ou le décalage changent : la photo peut devoir changer de dossier sur le SSD. */
    val changesCaptureDate: Boolean
        get() = captureDateTime != FieldChange.Keep || utcOffsetMinutes != FieldChange.Keep || dateShift?.isZero == false

    val isEmpty: Boolean
        get() = description == FieldChange.Keep && artist == FieldChange.Keep && copyright == FieldChange.Keep &&
            location == FieldChange.Keep && !removeSensitiveData && !changesCaptureDate
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

/** Récapitulatif avant application d'une modification groupée. */
data class MetadataEditPreview(
    val editable: Int,
    /** Éléments non éditables (format, vidéo…) qui seront ignorés. */
    val skipped: Int,
    /** Photos du SSD qui changeront de dossier jour. */
    val toMove: Int,
)

/** Bilan d'une modification de métadonnées. */
data class MetadataEditSummary(
    val modified: Int = 0,
    /** Fichiers déplacés dans un autre dossier jour du SSD. */
    val moved: Int = 0,
    /** Éléments non éditables (format, vidéo…) ignorés. */
    val skipped: Int = 0,
    val failedNames: List<String> = emptyList(),
    /** Droit d'écriture refusé : rien n'a été modifié. */
    val cancelled: Boolean = false,
    val ssdUnavailable: Boolean = false,
)
