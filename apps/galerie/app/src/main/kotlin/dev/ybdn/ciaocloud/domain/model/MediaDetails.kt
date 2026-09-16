package dev.ybdn.ciaocloud.domain.model

import java.time.LocalDateTime

/** Métadonnées détaillées d'un média, lues à la demande (panneau d'infos). */
data class MediaDetails(
    /** Heure locale de prise de vue (EXIF), si connue. */
    val captureLocalDateTime: LocalDateTime? = null,
    val captureUtcOffsetMinutes: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMillis: Long? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val lensModel: String? = null,
    val iso: Int? = null,
    val fNumber: Double? = null,
    /** Temps de pose en secondes. */
    val exposureTimeSeconds: Double? = null,
    val focalLengthMm: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
