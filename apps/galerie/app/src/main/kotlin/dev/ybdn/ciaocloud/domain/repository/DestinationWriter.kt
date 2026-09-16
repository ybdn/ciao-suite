package dev.ybdn.ciaocloud.domain.repository

/** Résultat d'une écriture de fichier vers la destination (SSD). */
data class DestinationWriteResult(
    /** Chemin complet relatif à la racine de destination, ex. "DCIM/2025/04/21/IMG_xxx.jpg". */
    val writtenRelativePath: String,
    /** Nom réellement écrit, qui peut différer du nom demandé si le système l'a ajusté. */
    val writtenFileName: String,
    val bytesWritten: Long,
    val checksum: String,
)

/**
 * Écriture vers la destination (SSD via SAF/DocumentFile). Le domaine ne connaît que des
 * chemins relatifs sous la racine choisie par l'utilisateur, jamais d'URI Android.
 */
interface DestinationWriter {

    /** Vérifie que la racine de destination est accessible en écriture (SSD branché, permission SAF valide). */
    suspend fun isDestinationAvailable(): Boolean

    /** Nom lisible de la destination (ex. nom du volume), ou null si inconnu. */
    suspend fun destinationName(): String?

    /**
     * Si la destination est dans le stockage interne du téléphone (cas d'un test sans SSD), son
     * chemin relatif MediaStore (ex. "Documents/Test/"), sinon null. Les copies y sont indexées par
     * MediaStore et ne doivent pas être re-proposées au scan.
     */
    suspend fun phoneStorageRelativePath(): String?

    /** Espace disponible sur la destination en octets, ou null si non calculable. */
    suspend fun availableBytes(): Long?

    /** Liste les noms de fichiers déjà présents dans [relativeDirPath], créant le dossier si absent. */
    suspend fun listExistingFileNames(relativeDirPath: String): Set<String>

    /**
     * Copie l'original de [sourceUri] (URI content:// du média source, en String) vers
     * `[relativeDirPath]/[fileName]`, en créant les dossiers manquants, force l'écriture sur le
     * disque, puis renvoie le checksum CRC32 des octets écrits. En cas d'échec, le fichier
     * partiel est supprimé. [onProgress] reçoit périodiquement le nombre d'octets déjà copiés.
     */
    suspend fun writeFile(
        sourceUri: String,
        relativeDirPath: String,
        fileName: String,
        onProgress: (bytesCopied: Long) -> Unit,
    ): DestinationWriteResult

    /** Relit le fichier copié pour recalculer taille + checksum, en vue de la vérification. */
    suspend fun readBackForVerification(relativeDirPath: String, fileName: String): DestinationWriteResult?
}
