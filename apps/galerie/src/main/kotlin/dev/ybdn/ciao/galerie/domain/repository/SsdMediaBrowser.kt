package dev.ybdn.ciao.galerie.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** Fichier média trouvé en parcourant le SSD. */
data class SsdFileEntry(
    /** Chemin réel sous la racine, ex. "DCIM/2025/04/21/IMG_1234.jpg". */
    val relativePath: String,
    val sizeBytes: Long,
    val lastModifiedEpochMillis: Long,
)

/** Lecture du SSD pour la galerie : parcours complet, accès aux originaux, suppression. */
interface SsdMediaBrowser {

    /** true si le SSD est branché et la permission SAF valide ; mis à jour au branchement/débranchement. */
    val isAvailable: StateFlow<Boolean>

    /** Revérifie la disponibilité (ex. au retour dans l'app). */
    suspend fun refreshAvailability(): Boolean

    /**
     * Parcourt les dossiers conformes `DCIM/aaaa/MM/jj` et renvoie leurs fichiers médias, ou null si
     * le SSD est inaccessible. [onProgress] reçoit le nombre de dossiers jour parcourus et de fichiers trouvés.
     */
    suspend fun listDayFolderMedia(onProgress: (dayFoldersScanned: Int, filesFound: Int) -> Unit): List<SsdFileEntry>?

    /** URI (String) de l'original sur le SSD, ou null s'il est inaccessible. */
    suspend fun documentUri(relativePath: String): String?

    /** Suppression définitive du fichier sur le SSD. @return true si supprimé (ou déjà absent). */
    suspend fun delete(relativePath: String): Boolean
}

/**
 * Cache local des vignettes des médias du SSD, pour les afficher SSD débranché. Alimenté au
 * transfert depuis la vignette du téléphone, puis à l'affichage.
 */
interface SsdThumbnailCache {
    /** Enregistre la vignette de [phoneUri] comme vignette de [relativePath] ; sans effet en cas d'échec. */
    suspend fun seedFromPhone(phoneUri: String, relativePath: String)

    suspend fun remove(relativePath: String)

    suspend fun sizeBytes(): Long

    suspend fun clear()
}
