package dev.ybdn.ciaocloud.domain.util

import dev.ybdn.ciaocloud.domain.model.MediaType

/** Type d'un fichier média déduit de son extension, pour les fichiers du SSD (sans MediaStore). */
data class MediaFileType(
    val mediaType: MediaType,
    val mimeType: String,
)

object MediaFileTypes {

    private val BY_EXTENSION = mapOf(
        "jpg" to MediaFileType(MediaType.PHOTO, "image/jpeg"),
        "jpeg" to MediaFileType(MediaType.PHOTO, "image/jpeg"),
        "png" to MediaFileType(MediaType.PHOTO, "image/png"),
        "heic" to MediaFileType(MediaType.PHOTO, "image/heic"),
        "heif" to MediaFileType(MediaType.PHOTO, "image/heif"),
        "avif" to MediaFileType(MediaType.PHOTO, "image/avif"),
        "webp" to MediaFileType(MediaType.PHOTO, "image/webp"),
        "gif" to MediaFileType(MediaType.PHOTO, "image/gif"),
        "dng" to MediaFileType(MediaType.PHOTO, "image/x-adobe-dng"),
        "mp4" to MediaFileType(MediaType.VIDEO, "video/mp4"),
        "m4v" to MediaFileType(MediaType.VIDEO, "video/mp4"),
        "mov" to MediaFileType(MediaType.VIDEO, "video/quicktime"),
        "3gp" to MediaFileType(MediaType.VIDEO, "video/3gpp"),
        "mkv" to MediaFileType(MediaType.VIDEO, "video/x-matroska"),
        "webm" to MediaFileType(MediaType.VIDEO, "video/webm"),
    )

    /**
     * Fichier `._*` (AppleDouble) écrit par macOS sur un volume exFAT/FAT : il porte l'extension du
     * média qu'il accompagne mais ne contient que des métadonnées Finder.
     */
    fun isAppleDouble(fileName: String): Boolean = fileName.startsWith("._")

    /** null si l'extension n'est pas celle d'une photo ou vidéo reconnue, ou pour un fichier AppleDouble. */
    fun fromFileName(fileName: String): MediaFileType? {
        if (isAppleDouble(fileName)) return null
        val extension = FileNameCollisionResolver.splitBaseAndExtension(fileName).second
        return BY_EXTENSION[extension.lowercase()]
    }
}
