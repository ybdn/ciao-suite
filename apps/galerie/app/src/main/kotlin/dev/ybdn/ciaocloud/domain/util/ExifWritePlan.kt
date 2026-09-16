package dev.ybdn.ciaocloud.domain.util

/** Noms des balises EXIF (identiques aux constantes `ExifInterface`), sans dépendance Android. */
object ExifTags {
    const val ORIENTATION = "Orientation"
}

/**
 * Balises à écrire et à supprimer dans un fichier, sans toucher aux pixels ni aux autres balises.
 * Une balise ne peut pas être à la fois écrite et supprimée.
 */
data class ExifWritePlan(
    val set: Map<String, String> = emptyMap(),
    val remove: Set<String> = emptySet(),
) {
    init {
        require(set.keys.none { it in remove }) { "Balise à la fois écrite et supprimée" }
    }

    val isEmpty: Boolean get() = set.isEmpty() && remove.isEmpty()

    companion object {
        fun orientation(value: Int): ExifWritePlan {
            require(value in 1..8) { "Orientation invalide : $value" }
            return ExifWritePlan(set = mapOf(ExifTags.ORIENTATION to value.toString()))
        }
    }
}
