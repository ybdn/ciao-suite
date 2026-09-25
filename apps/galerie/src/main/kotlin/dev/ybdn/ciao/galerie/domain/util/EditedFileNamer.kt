package dev.ybdn.ciao.galerie.domain.util

/** Nom d'une copie retouchée : `<nom>_edit.<ext>`, puis `_edit_1`, `_edit_2`… (casse ignorée). */
object EditedFileNamer {

    const val SUFFIX = "_edit"

    /**
     * @param targetExtension extension de la copie si elle change de format (ex. `jpg` pour un HEIC),
     * sinon null (extension de l'original conservée).
     * @param existingNames noms déjà présents dans le dossier de la copie.
     */
    fun copyName(originalName: String, targetExtension: String?, existingNames: Collection<String>): String {
        val (base, extension) = FileNameCollisionResolver.splitBaseAndExtension(originalName)
        val finalExtension = targetExtension ?: extension
        val desired = if (finalExtension.isEmpty()) "$base$SUFFIX" else "$base$SUFFIX.$finalExtension"
        val taken = existingNames.mapTo(HashSet()) { it.lowercase() }
        return FileNameCollisionResolver.resolveAvailableName(desired) { it.lowercase() in taken }
    }
}
