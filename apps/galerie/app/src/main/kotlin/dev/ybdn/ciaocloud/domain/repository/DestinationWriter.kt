package dev.ybdn.ciaocloud.domain.repository

/** Résultat d'une écriture de fichier vers la destination (SSD). */
data class DestinationWriteResult(
    /** Chemin complet relatif à la racine de destination, ex. "DCIM/2025/04/21/IMG_xxx.jpg". */
    val writtenRelativePath: String,
    val bytesWritten: Long,
    val checksum: String,
)

/**
 * Écriture vers la destination (SSD via SAF/DocumentFile). Le domaine ne connaît que des
 * chemins relatifs sous la racine choisie par l'utilisateur, jamais d'URI Android.
 */
interface DestinationWriter {

    /** Vérifie que la racine de destination est toujours accessible (permission SAF valide). */
    suspend fun isDestinationAvailable(): Boolean

    /** Espace disponible sur la destination en octets, ou null si non calculable. */
    suspend fun availableBytes(): Long?

    /** Liste les noms de fichiers déjà présents dans [relativeDirPath], créant le dossier si absent. */
    suspend fun listExistingFileNames(relativeDirPath: String): Set<String>

    /**
     * Copie [sourceUri] (URI content:// du média source, en String) vers
     * `[relativeDirPath]/[fileName]`, en créant les dossiers manquants, puis calcule le
     * checksum (CRC32/MD5) du fichier écrit pour vérification ultérieure.
     */
    suspend fun writeFile(
        sourceUri: String,
        sourceSizeBytes: Long,
        relativeDirPath: String,
        fileName: String,
    ): DestinationWriteResult

    /** Relit le fichier copié pour recalculer taille + checksum, en vue de la vérification. */
    suspend fun readBackForVerification(relativeDirPath: String, fileName: String): DestinationWriteResult?
}
