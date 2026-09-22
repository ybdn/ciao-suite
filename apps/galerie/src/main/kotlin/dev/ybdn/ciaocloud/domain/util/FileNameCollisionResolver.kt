package dev.ybdn.ciaocloud.domain.util

/**
 * Résolution des collisions de nom à destination : si un fichier de même nom existe déjà,
 * ajoute un suffixe `_1`, `_2`, ... avant l'extension plutôt que d'écraser le fichier existant.
 */
object FileNameCollisionResolver {

    /**
     * @param desiredName nom de fichier souhaité (avec extension), ex. "IMG_20250421_143022.jpg".
     * @param nameExists prédicat indiquant si un nom donné existe déjà dans le dossier de destination.
     * @return un nom disponible, égal à [desiredName] si aucune collision.
     */
    fun resolveAvailableName(
        desiredName: String,
        nameExists: (String) -> Boolean,
    ): String {
        if (!nameExists(desiredName)) return desiredName

        val (base, extension) = splitBaseAndExtension(desiredName)
        var suffix = 1
        var candidate: String
        do {
            candidate = if (extension.isEmpty()) {
                "${base}_$suffix"
            } else {
                "${base}_$suffix.$extension"
            }
            suffix++
        } while (nameExists(candidate))

        return candidate
    }

    /** Sépare "IMG_0001.jpg" en ("IMG_0001", "jpg") ; extension vide si le nom n'en a pas. */
    fun splitBaseAndExtension(fileName: String): Pair<String, String> {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex <= 0 || dotIndex == fileName.length - 1) {
            fileName to ""
        } else {
            fileName.substring(0, dotIndex) to fileName.substring(dotIndex + 1)
        }
    }
}
