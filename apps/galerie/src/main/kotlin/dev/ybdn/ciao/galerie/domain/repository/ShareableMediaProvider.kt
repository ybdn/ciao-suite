package dev.ybdn.ciao.galerie.domain.repository

import dev.ybdn.ciao.galerie.domain.util.StripTarget
import kotlinx.coroutines.flow.Flow

/** Fichier prêt à être partagé avec une autre app. */
data class ShareableMedia(
    val uri: String,
    val mimeType: String,
)

/** Préférences du partage (réglage « Partager sans métadonnées », désactivé par défaut). */
interface SharePreferences {
    fun observeStripMetadata(): Flow<Boolean>

    suspend fun setStripMetadata(enabled: Boolean)
}

/** Raison pour laquelle une copie sans métadonnées n'a pas pu être produite. */
enum class StripFailureReason {
    UNSUPPORTED_FORMAT,
    UNREADABLE_SOURCE,
    DECODE_FAILED,

    /** Piste vidéo ou audio refusée par le remultiplexeur. */
    TRACK_REJECTED,

    /** Le contrôle après nettoyage a trouvé une métadonnée personnelle. */
    METADATA_REMAINING,
    WRITE_FAILED,
}

sealed interface StripOutcome {
    data class Success(val media: ShareableMedia) : StripOutcome
    data class Failure(val reason: StripFailureReason) : StripOutcome
}

/**
 * Copies temporaires des médias partagés (cache de l'app, exposées par `FileProvider`, effacées au
 * lancement suivant). Elles ne sont jamais écrites dans MediaStore ni sur le SSD.
 */
interface ShareableMediaProvider {

    /** Espace libre où sont préparées les copies, null si inconnu. */
    suspend fun availableBytes(): Long?

    /** Crée un lot de copies ; renvoie son identifiant. */
    suspend fun createBatch(): String

    /** Copie à l'identique (média illisible hors de l'app : SSD, `file://`) ; null si la source est illisible. */
    suspend fun copy(batchId: String, index: Int, sourceUri: String, fileName: String, mimeType: String): ShareableMedia?

    /** Copie sans métadonnées selon [target], contrôlée avant d'être rendue (voir [MetadataStripper]). */
    suspend fun copyWithoutMetadata(batchId: String, index: Int, sourceUri: String, target: StripTarget): StripOutcome

    suspend fun deleteBatch(batchId: String)

    /** Supprime les copies temporaires des partages précédents. */
    suspend fun clearTemporaryCopies()
}

/**
 * Produit une copie sans métadonnées d'un média : photo réencodée (orientation appliquée aux
 * pixels, profil ICC et carte de gain conservés), vidéo remultiplexée sans réencodage. La copie est
 * relue et refusée s'il subsiste une donnée personnelle ; en cas d'échec, rien n'est laissé à [outputPath].
 */
interface MetadataStripper {
    /** @return null si la copie est prête et contrôlée, sinon la raison de l'échec. */
    suspend fun strip(sourceUri: String, target: StripTarget, outputPath: String): StripFailureReason?
}
