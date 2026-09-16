package dev.ybdn.ciaocloud.domain.repository

import dev.ybdn.ciaocloud.domain.model.FileFingerprint
import dev.ybdn.ciaocloud.domain.model.TransferRecord
import dev.ybdn.ciaocloud.domain.util.ExifWritePlan
import kotlinx.coroutines.flow.StateFlow

/** Transfert vers le SSD en cours : l'édition est alors désactivée (fichier en cours de copie). */
interface TransferActivity {
    val isRunning: StateFlow<Boolean>
}

/**
 * Fichiers de travail complets dans le stockage de l'app (spec v3 A4) : toute écriture vers un
 * original passe d'abord par un fichier de travail vérifié.
 */
interface EditWorkspace {
    /** Chemin d'un nouveau fichier de travail (inexistant) portant l'extension [extension]. */
    suspend fun newWorkFile(extension: String): String

    /** Copie octet pour octet l'original [sourceUri] (avec sa position GPS pour une URI MediaStore) vers [path]. */
    suspend fun copyOriginal(sourceUri: String, path: String)

    /** Taille et CRC32 de [path], null s'il n'existe pas. */
    suspend fun fingerprint(path: String): FileFingerprint?

    suspend fun delete(path: String)
}

/** Écriture de balises EXIF dans un fichier de travail (`ExifInterface.saveAttributes`). */
interface MetadataWriter {
    /** Valeur de la balise `Orientation` (1 si absente). */
    suspend fun readOrientation(path: String): Int

    suspend fun apply(path: String, plan: ExifWritePlan)
}

/** Droit d'écriture sur des originaux du téléphone (`MediaStore.createWriteRequest`, confirmation système). */
interface MediaWriteAccess {
    /** @return true si l'utilisateur a accepté. */
    suspend fun request(uris: List<String>): Boolean
}

/** Écritures vers les médias du téléphone (MediaStore). Chaque écriture est relue et vérifiée. */
interface PhoneMediaWriter {
    /** Taille et CRC32 de l'original, null s'il n'existe plus. */
    suspend fun fingerprint(uri: String): FileFingerprint?

    /** Noms des fichiers du dossier MediaStore [relativePath] (ex. `DCIM/Camera/`). */
    suspend fun existingNames(relativePath: String): Set<String>

    /** Crée une entrée en attente (`IS_PENDING = 1`) ; renvoie son URI. */
    suspend fun createPending(
        relativePath: String,
        displayName: String,
        mimeType: String,
        dateTakenEpochMillis: Long?,
    ): String

    /** Écrit [workPath] dans l'entrée en attente et vérifie la relecture ; lève une exception sinon. */
    suspend fun writePending(uri: String, workPath: String, expected: FileFingerprint)

    /** Publie l'entrée (`IS_PENDING = 0`) ; renvoie son identifiant MediaStore. */
    suspend fun publish(uri: String): Long

    /** Supprime l'entrée si elle est encore en attente (sans effet si absente ou publiée). */
    suspend fun deleteIfPending(uri: String)

    /**
     * Remplace le contenu de [uri] par [workPath] : sauvegarde complète de l'original dans [backupPath],
     * écriture, relecture. En cas d'échec, l'original est réécrit depuis la sauvegarde puis l'exception
     * est propagée. La sauvegarde est supprimée en cas de succès.
     */
    suspend fun replace(uri: String, workPath: String, expected: FileFingerprint, backupPath: String)

    /** true si une sauvegarde complète existe à [backupPath]. */
    suspend fun backupExists(backupPath: String): Boolean

    /** Réécrit [uri] depuis la sauvegarde [backupPath] (reprise) ; lève une exception en cas d'échec. */
    suspend fun restoreFromBackup(uri: String, backupPath: String)

    suspend fun deleteBackup(backupPath: String)
}

/**
 * Primitives d'écriture sur le SSD (SAF). Pas de renommage atomique : le remplacement sûr est
 * orchestré par le domaine (`<nom>.ciao-new` → `<nom>.ciao-old` → `<nom>`).
 */
interface SsdMediaWriter {
    suspend fun isAvailable(): Boolean

    /** Noms des fichiers de [relativeDir], null si le SSD est inaccessible. */
    suspend fun listNames(relativeDir: String): List<String>?

    suspend fun fingerprint(relativePath: String): FileFingerprint?

    suspend fun exists(relativePath: String): Boolean

    /**
     * Écrit [workPath] vers [relativePath] (fichier inexistant, dossier créé si besoin), force l'écriture
     * sur le disque, relit et vérifie ; en cas d'échec, le fichier est supprimé et l'exception propagée.
     */
    suspend fun write(relativePath: String, workPath: String, expected: FileFingerprint)

    /** Renomme [relativePath] en [newName] (même dossier) ; lève une exception si le nom obtenu diffère. */
    suspend fun rename(relativePath: String, newName: String)

    /** @return true si supprimé ou déjà absent. */
    suspend fun delete(relativePath: String): Boolean
}

enum class EditStepKind { COPY, REPLACE }

/** Écriture en cours sur un fichier du téléphone. */
data class PhoneEditStep(
    val kind: EditStepKind,
    /** URI de l'original (remplacement) ou de la copie en attente (null tant qu'elle n'est pas créée). */
    val uri: String?,
    val newFingerprint: FileFingerprint,
    /** Remplacement : CRC32 de l'original avant écriture. */
    val originalChecksum: String? = null,
    /** Remplacement : sauvegarde de l'original. */
    val backupPath: String? = null,
)

/** Écriture en cours sur un fichier du SSD. */
data class SsdEditStep(
    val kind: EditStepKind,
    /** Chemin final du fichier (remplacé ou créé). */
    val relativePath: String,
    val newFingerprint: FileFingerprint,
    val originalChecksum: String? = null,
)

/**
 * Opération d'écriture en cours, persistée avant toute modification pour être reprise au lancement
 * suivant si l'app est tuée (spec v3 A4, journal de reprise).
 */
data class EditJournalEntry(
    val id: String,
    val phone: PhoneEditStep? = null,
    val ssd: SsdEditStep? = null,
    /** Enregistrements de transfert retirés avant l'écriture, restaurés si les fichiers sont inchangés. */
    val previousRecords: List<TransferRecord> = emptyList(),
    /** Média modifié aux deux endroits : son enregistrement est mis à jour si les deux fichiers sont identiques. */
    val editedMediaStoreId: Long? = null,
)

interface EditJournal {
    suspend fun write(entry: EditJournalEntry)

    suspend fun remove(id: String)

    suspend fun entries(): List<EditJournalEntry>

    /** Emplacement de la sauvegarde d'un original du téléphone pour l'opération [id] (hors cache). */
    fun backupPath(id: String): String
}
