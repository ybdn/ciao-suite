package dev.ybdn.ciao.galerie.domain.util

import kotlin.math.ceil

/** Traitement appliqué à un média pour en produire une copie sans métadonnées. */
enum class StripStrategy { REENCODE_JPEG, REENCODE_PNG, REENCODE_WEBP, CONVERT_TO_JPEG, REMUX_VIDEO, UNSUPPORTED }

/** Conteneur de sortie d'une vidéo remultiplexée (formats acceptés par `MediaMuxer`). */
enum class VideoContainer { MP4, THREE_GPP, WEBM }

/** Copie à produire : traitement, nom et type MIME du fichier partagé. */
data class StripTarget(
    val strategy: StripStrategy,
    val fileName: String,
    val mimeType: String,
    /** Renseigné pour [StripStrategy.REMUX_VIDEO] uniquement. */
    val videoContainer: VideoContainer? = null,
)

/**
 * Plan du partage sans métadonnées (spec v3 D4) : type MIME source → traitement, nom et type de la
 * copie. Les photos sont réencodées (aucune balise oubliée par construction), les vidéos
 * remultiplexées sans réencodage. Le GIF est refusé : l'animation serait perdue.
 */
object MetadataStripPlan {

    /** Marge d'estimation de l'espace nécessaire (réencodage d'un format plus compact que le JPEG). */
    const val SPACE_MARGIN = 1.2

    private val JPEG_TYPES = setOf("image/jpeg", "image/jpg", "image/pjpeg")
    private val CONVERTED_TYPES = setOf("image/heic", "image/heif", "image/avif", "image/x-adobe-dng", "image/dng")
    private val MP4_TYPES = setOf("video/mp4", "video/quicktime", "video/x-m4v")

    fun strategyFor(mimeType: String): StripStrategy = when (normalize(mimeType)) {
        in JPEG_TYPES -> StripStrategy.REENCODE_JPEG
        "image/png" -> StripStrategy.REENCODE_PNG
        "image/webp" -> StripStrategy.REENCODE_WEBP
        in CONVERTED_TYPES -> StripStrategy.CONVERT_TO_JPEG
        in MP4_TYPES, "video/3gpp", "video/webm" -> StripStrategy.REMUX_VIDEO
        else -> StripStrategy.UNSUPPORTED
    }

    /**
     * Type MIME générique ou inconnu (URI reçue d'une autre app) : l'extension du nom de fichier
     * sert alors de repli. Un type explicitement refusé (GIF) le reste.
     */
    fun targetFor(displayName: String, mimeType: String): StripTarget {
        val declared = normalize(mimeType)
        val effectiveType = if (strategyFor(declared) == StripStrategy.UNSUPPORTED && declared != "image/gif") {
            MediaFileTypes.fromFileName(displayName)?.mimeType ?: declared
        } else {
            declared
        }
        val strategy = strategyFor(effectiveType)
        val (base, extension) = FileNameCollisionResolver.splitBaseAndExtension(displayName)
        fun named(canonical: String, accepted: Set<String>) =
            if (extension.lowercase() in accepted) displayName else "$base.$canonical"

        return when (strategy) {
            StripStrategy.REENCODE_JPEG -> StripTarget(strategy, named("jpg", setOf("jpg", "jpeg")), "image/jpeg")
            StripStrategy.REENCODE_PNG -> StripTarget(strategy, named("png", setOf("png")), "image/png")
            StripStrategy.REENCODE_WEBP -> StripTarget(strategy, named("webp", setOf("webp")), "image/webp")
            StripStrategy.CONVERT_TO_JPEG -> StripTarget(strategy, "$base.jpg", "image/jpeg")
            StripStrategy.REMUX_VIDEO -> when (effectiveType) {
                "video/3gpp" -> StripTarget(strategy, named("3gp", setOf("3gp")), "video/3gpp", VideoContainer.THREE_GPP)
                "video/webm" -> StripTarget(strategy, named("webm", setOf("webm")), "video/webm", VideoContainer.WEBM)
                else -> StripTarget(strategy, named("mp4", setOf("mp4", "m4v")), "video/mp4", VideoContainer.MP4)
            }
            StripStrategy.UNSUPPORTED -> StripTarget(strategy, displayName, declared)
        }
    }

    /** Espace à prévoir dans le cache pour les copies : somme des tailles sources × [SPACE_MARGIN]. */
    fun estimateRequiredBytes(sourceSizes: Collection<Long>): Long =
        ceil(sourceSizes.sumOf { it.coerceAtLeast(0) } * SPACE_MARGIN).toLong()

    private fun normalize(mimeType: String): String = mimeType.substringBefore(';').trim().lowercase()
}
