package dev.ybdn.ciaocloud.domain.util

/**
 * Contrôle d'une copie nettoyée avant partage (spec v3 D5, défense en profondeur) : une seule donnée
 * personnelle restante fait échouer le média, sans repli vers l'original.
 */
object StrippedMetadataCheck {

    /** Balises EXIF (noms `ExifInterface`) qui ne doivent pas subsister dans une photo partagée. */
    val FORBIDDEN_EXIF_TAGS: List<String> = listOf(
        // Dates et décalages
        "DateTime", "DateTimeOriginal", "DateTimeDigitized",
        "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized",
        "SubSecTime", "SubSecTimeOriginal", "SubSecTimeDigitized",
        // Position
        "GPSLatitude", "GPSLongitude", "GPSAltitude", "GPSTimeStamp", "GPSDateStamp",
        "GPSImgDirection", "GPSProcessingMethod", "GPSAreaInformation",
        // Appareil, objectif et identifiants
        "Make", "Model", "LensMake", "LensModel", "LensSpecification",
        "BodySerialNumber", "LensSerialNumber", "CameraOwnerName", "ImageUniqueID",
        // Logiciel et textes
        "Software", "Artist", "Copyright", "ImageDescription", "UserComment", "MakerNote",
        // Paramètres de prise de vue
        "ExposureTime", "FNumber", "PhotographicSensitivity", "FocalLength", "FocalLengthIn35mmFilm",
    )

    /**
     * Préfixes XMP admis : structure RDF et descripteurs techniques de la carte de gain Ultra HDR
     * (`hdrgm`, `Container`, `Item`), rattachée volontairement à la copie (spec v3 D3).
     */
    private val ALLOWED_XMP_PREFIXES = setOf("x", "rdf", "xmlns", "xml", "hdrgm", "Container", "Item")

    private val QUALIFIED_ELEMENT = Regex("""</?\s*([A-Za-z_][\w.-]*):[A-Za-z_][\w.-]*""")
    private val QUALIFIED_ATTRIBUTE = Regex("""\s([A-Za-z_][\w.-]*):[A-Za-z_][\w.-]*\s*=""")

    /** Préfixes XMP présents hors liste blanche (ex. `exif`, `GCamera`, `xmp`), vide si le XMP est acceptable. */
    fun disallowedXmpPrefixes(xmp: String?): Set<String> {
        if (xmp.isNullOrBlank()) return emptySet()
        return (QUALIFIED_ELEMENT.findAll(xmp) + QUALIFIED_ATTRIBUTE.findAll(xmp))
            .map { it.groupValues[1] }
            .filterNot { it in ALLOWED_XMP_PREFIXES }
            .toSet()
    }

    /**
     * Date d'une vidéo (`METADATA_KEY_DATE`, ex. `20260916T101010.000Z`) révélant quand elle a été
     * prise ou copiée. Absente, illisible ou antérieure à 1971 (horloge MP4 remise à zéro : 1904) :
     * inexploitable.
     */
    fun isMeaningfulVideoDate(value: String?): Boolean {
        val year = value?.trim()?.take(4)?.toIntOrNull() ?: return false
        return year > 1970
    }
}
