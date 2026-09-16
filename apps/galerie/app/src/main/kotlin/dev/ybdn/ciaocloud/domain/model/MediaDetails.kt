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
    val lensMake: String? = null,
    val lensModel: String? = null,
    val iso: Int? = null,
    val fNumber: Double? = null,
    /** Temps de pose en secondes. */
    val exposureTimeSeconds: Double? = null,
    val focalLengthMm: Double? = null,
    /** Focale équivalente en 24×36. */
    val focalLength35mm: Int? = null,
    /** Correction d'exposition en IL (EV). */
    val exposureBiasEv: Double? = null,
    val exposureProgram: ExposureProgram? = null,
    val meteringMode: MeteringMode? = null,
    val flashFired: Boolean? = null,
    val whiteBalanceManual: Boolean? = null,
    val sceneType: SceneType? = null,
    /** Facteur de zoom numérique, seulement s'il est supérieur à 1. */
    val digitalZoomRatio: Double? = null,
    val subjectDistanceMeters: Double? = null,
    /** Rotation à appliquer pour l'affichage (0, 90, 180, 270). */
    val rotationDegrees: Int? = null,
    val software: String? = null,
    val artist: String? = null,
    val copyright: String? = null,
    val description: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    /** Direction de visée en degrés (0 = nord). */
    val directionDegrees: Double? = null,
    /** Type MIME du conteneur vidéo (ex. `video/mp4`). */
    val containerMimeType: String? = null,
    /** Type MIME du flux vidéo (ex. `video/hevc`). */
    val videoCodecMimeType: String? = null,
    /** Type MIME du flux audio (ex. `audio/mp4a-latm`), null s'il n'y a pas d'audio. */
    val audioCodecMimeType: String? = null,
    val hasAudio: Boolean? = null,
    val audioSampleRateHz: Int? = null,
    val audioChannels: Int? = null,
    val frameRate: Double? = null,
    val bitrateBitsPerSecond: Long? = null,
    val hdrFormat: HdrFormat? = null,
)

enum class ExposureProgram { MANUAL, NORMAL, APERTURE_PRIORITY, SHUTTER_PRIORITY, CREATIVE, ACTION, PORTRAIT, LANDSCAPE }

enum class MeteringMode { AVERAGE, CENTER_WEIGHTED, SPOT, MULTI_SPOT, PATTERN, PARTIAL }

enum class SceneType { LANDSCAPE, PORTRAIT, NIGHT }

enum class HdrFormat { HDR10, HLG }
