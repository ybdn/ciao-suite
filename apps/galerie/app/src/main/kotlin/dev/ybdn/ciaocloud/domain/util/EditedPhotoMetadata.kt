package dev.ybdn.ciaocloud.domain.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Métadonnées d'une photo retouchée (spec v3 B6) : le fichier encodé n'en contient aucune, elles sont
 * recopiées de l'original selon une liste blanche. Ni vignette EXIF (image non recadrée), ni
 * `MakerNote`, ni XMP (photo animée), ni identifiants uniques.
 */
object EditedPhotoMetadata {

    const val SOFTWARE = "C!ao"

    private val EXIF_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)

    val COPIED_TAGS: List<String> = listOf(
        // Dates et décalages
        "DateTimeOriginal", "DateTimeDigitized", "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized",
        "SubSecTime", "SubSecTimeOriginal", "SubSecTimeDigitized",
        // Appareil et objectif
        "Make", "Model", "LensMake", "LensModel", "LensSpecification",
        // Paramètres de prise de vue
        "ExposureTime", "FNumber", "ExposureProgram", "PhotographicSensitivity", "ShutterSpeedValue",
        "ApertureValue", "BrightnessValue", "ExposureBiasValue", "MaxApertureValue", "SubjectDistance",
        "MeteringMode", "LightSource", "Flash", "FocalLength", "FocalLengthIn35mmFilm", "WhiteBalance",
        "DigitalZoomRatio", "SceneCaptureType", "ExposureMode", "SensingMethod", "SubjectDistanceRange",
    ) + ExifTags.GPS + listOf(
        // Textes et droits
        ExifTags.IMAGE_DESCRIPTION, ExifTags.ARTIST, ExifTags.COPYRIGHT,
    )

    /** Balises propres au fichier produit : pixels déjà orientés, dimensions réelles, logiciel, date. */
    fun overrides(widthPx: Int, heightPx: Int, now: LocalDateTime): ExifWritePlan = ExifWritePlan(
        set = mapOf(
            ExifTags.ORIENTATION to OrientationCodec.NORMAL.toString(),
            "ImageWidth" to widthPx.toString(),
            "ImageLength" to heightPx.toString(),
            "PixelXDimension" to widthPx.toString(),
            "PixelYDimension" to heightPx.toString(),
            "Software" to SOFTWARE,
            "DateTime" to EXIF_DATE.format(now),
        ),
    )
}
