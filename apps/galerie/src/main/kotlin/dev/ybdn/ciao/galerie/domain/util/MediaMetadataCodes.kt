package dev.ybdn.ciao.galerie.domain.util

import dev.ybdn.ciao.galerie.domain.model.ExposureProgram
import dev.ybdn.ciao.galerie.domain.model.HdrFormat
import dev.ybdn.ciao.galerie.domain.model.MeteringMode
import dev.ybdn.ciao.galerie.domain.model.SceneType

/** Décodage des valeurs numériques EXIF 2.3 et des métadonnées de conteneur vidéo. */
object MediaMetadataCodes {

    fun exposureProgram(code: Int?): ExposureProgram? = when (code) {
        1 -> ExposureProgram.MANUAL
        2 -> ExposureProgram.NORMAL
        3 -> ExposureProgram.APERTURE_PRIORITY
        4 -> ExposureProgram.SHUTTER_PRIORITY
        5 -> ExposureProgram.CREATIVE
        6 -> ExposureProgram.ACTION
        7 -> ExposureProgram.PORTRAIT
        8 -> ExposureProgram.LANDSCAPE
        else -> null
    }

    fun meteringMode(code: Int?): MeteringMode? = when (code) {
        1 -> MeteringMode.AVERAGE
        2 -> MeteringMode.CENTER_WEIGHTED
        3 -> MeteringMode.SPOT
        4 -> MeteringMode.MULTI_SPOT
        5 -> MeteringMode.PATTERN
        6 -> MeteringMode.PARTIAL
        else -> null
    }

    /** Scène standard (0) ignorée : elle n'apporte rien à l'affichage. */
    fun sceneType(code: Int?): SceneType? = when (code) {
        1 -> SceneType.LANDSCAPE
        2 -> SceneType.PORTRAIT
        3 -> SceneType.NIGHT
        else -> null
    }

    /** Bit 0 : flash déclenché. Bit 5 : pas de flash sur l'appareil (information alors sans objet). */
    fun flashFired(code: Int?): Boolean? = when {
        code == null || code < 0 -> null
        code and 0x20 != 0 -> null
        else -> code and 0x01 != 0
    }

    /** 0 = automatique, 1 = manuelle. */
    fun whiteBalanceManual(code: Int?): Boolean? = when (code) {
        0 -> false
        1 -> true
        else -> null
    }

    /** `COLOR_TRANSFER` Android : 6 = ST 2084 (PQ, HDR10), 7 = HLG. */
    fun hdrFormat(colorTransfer: Int?): HdrFormat? = when (colorTransfer) {
        6 -> HdrFormat.HDR10
        7 -> HdrFormat.HLG
        else -> null
    }

    /** Cadence moyenne à partir du nombre d'images et de la durée, null si incohérente. */
    fun frameRate(frameCount: Int?, durationMillis: Long?): Double? {
        if (frameCount == null || durationMillis == null || frameCount <= 0 || durationMillis <= 0) return null
        return (frameCount * 1000.0 / durationMillis).takeIf { it in 1.0..1000.0 }
    }

    /** Nom lisible d'un codec à partir de son type MIME Android. */
    fun codecName(mimeType: String): String = when (mimeType.lowercase()) {
        "video/hevc" -> "HEVC (H.265)"
        "video/avc" -> "AVC (H.264)"
        "video/av01" -> "AV1"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/x-vnd.on2.vp8" -> "VP8"
        "video/dolby-vision" -> "Dolby Vision"
        "video/mp4v-es" -> "MPEG-4"
        "video/3gpp" -> "H.263"
        "audio/mp4a-latm" -> "AAC"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        "audio/flac" -> "FLAC"
        "audio/mpeg" -> "MP3"
        "audio/3gpp" -> "AMR-NB"
        "audio/amr-wb" -> "AMR-WB"
        "audio/ac3" -> "AC-3"
        "audio/eac3" -> "E-AC-3"
        "audio/raw" -> "PCM"
        else -> fallbackName(mimeType)
    }

    /** Nom lisible d'un conteneur vidéo à partir de son type MIME. */
    fun containerName(mimeType: String): String = when (mimeType.lowercase()) {
        "video/mp4" -> "MP4"
        "video/quicktime" -> "QuickTime (MOV)"
        "video/webm" -> "WebM"
        "video/x-matroska" -> "Matroska (MKV)"
        "video/3gpp" -> "3GP"
        else -> fallbackName(mimeType)
    }

    private fun fallbackName(mimeType: String): String =
        mimeType.substringAfter('/').removePrefix("x-").uppercase()
}
