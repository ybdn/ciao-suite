package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.repository.ShareableMedia
import dev.ybdn.ciaocloud.domain.repository.ShareableMediaProvider
import dev.ybdn.ciaocloud.domain.repository.SharePreferences
import dev.ybdn.ciaocloud.domain.repository.SsdMediaBrowser
import dev.ybdn.ciaocloud.domain.repository.StripOutcome
import dev.ybdn.ciaocloud.domain.util.MetadataStripPlan
import dev.ybdn.ciaocloud.domain.util.StripStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

sealed interface ShareOutcome {
    data class Ready(val media: List<ShareableMedia>) : ShareOutcome

    /**
     * Certains médias n'ont pas pu être nettoyés : l'utilisateur partage les autres ([media], si non
     * vide) ou annule (copies supprimées par [PrepareShareUseCase.discard]).
     */
    data class PartiallyFailed(
        val media: List<ShareableMedia>,
        val failedNames: List<String>,
        val batchId: String,
    ) : ShareOutcome

    data class InsufficientSpace(val requiredBytes: Long) : ShareOutcome

    /** Un média du SSD est inaccessible (SSD débranché). */
    data object SsdUnavailable : ShareOutcome
}

/**
 * Prépare les URI d'un partage. Réglage désactivé (comportement v2) : URI MediaStore partagées
 * directement, copie à l'identique pour les médias illisibles hors de l'app (SSD, `file://`).
 * Réglage activé : **chaque** média passe par une copie sans métadonnées, aucune URI d'original n'est
 * transmise, et un média non nettoyé n'est jamais remplacé par son original.
 */
class PrepareShareUseCase(
    private val sharePreferences: SharePreferences,
    private val shareableMediaProvider: ShareableMediaProvider,
    private val ssdMediaBrowser: SsdMediaBrowser,
) {
    /** @param onProgress (média en cours, total), appelé seulement lors du retrait des métadonnées. */
    suspend operator fun invoke(
        items: List<GalleryItem>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): ShareOutcome {
        val sources = items.map { item ->
            val uri = item.phone?.uri
                ?: item.ssd?.let { ssdMediaBrowser.documentUri(it.relativePath) }
                ?: return ShareOutcome.SsdUnavailable
            item to uri
        }
        return if (sharePreferences.observeStripMetadata().first()) {
            prepareWithoutMetadata(sources, onProgress)
        } else {
            prepareOriginals(sources)
        }
    }

    /** Annule un partage partiellement préparé : les copies produites sont supprimées. */
    suspend fun discard(outcome: ShareOutcome.PartiallyFailed) = shareableMediaProvider.deleteBatch(outcome.batchId)

    suspend fun clearTemporaryCopies() = shareableMediaProvider.clearTemporaryCopies()

    private suspend fun prepareOriginals(sources: List<Pair<GalleryItem, String>>): ShareOutcome {
        var batchId: String? = null
        return withBatchCleanup({ batchId }) {
            val media = sources.mapIndexed { index, (item, uri) ->
                // Les URI content:// du téléphone se partagent telles quelles ; une URI file:// est
                // interdite hors du processus et un document SAF n'est pas lu par toutes les apps.
                if (item.phone != null && uri.startsWith(CONTENT_SCHEME)) return@mapIndexed ShareableMedia(uri, item.mimeType)
                val batch = batchId ?: shareableMediaProvider.createBatch().also { batchId = it }
                shareableMediaProvider.copy(batch, index, uri, item.displayName, item.mimeType)
                    ?: run {
                        shareableMediaProvider.deleteBatch(batch)
                        return@withBatchCleanup ShareOutcome.SsdUnavailable
                    }
            }
            ShareOutcome.Ready(media)
        }
    }

    private suspend fun prepareWithoutMetadata(
        sources: List<Pair<GalleryItem, String>>,
        onProgress: (current: Int, total: Int) -> Unit,
    ): ShareOutcome {
        val required = MetadataStripPlan.estimateRequiredBytes(sources.map { it.first.sizeBytes })
        val available = shareableMediaProvider.availableBytes()
        if (available != null && available < required) return ShareOutcome.InsufficientSpace(required)

        val batchId = shareableMediaProvider.createBatch()
        return withBatchCleanup({ batchId }) {
            val ready = ArrayList<ShareableMedia>()
            val failedNames = ArrayList<String>()
            sources.forEachIndexed { index, (item, uri) ->
                onProgress(index + 1, sources.size)
                val target = MetadataStripPlan.targetFor(item.displayName, item.mimeType)
                val outcome = if (target.strategy == StripStrategy.UNSUPPORTED) {
                    null
                } else {
                    shareableMediaProvider.copyWithoutMetadata(batchId, index, uri, target)
                }
                if (outcome is StripOutcome.Success) ready += outcome.media else failedNames += item.displayName
            }
            when {
                failedNames.isEmpty() -> ShareOutcome.Ready(ready)
                else -> ShareOutcome.PartiallyFailed(ready, failedNames, batchId)
            }
        }
    }

    /** Annulation (ou erreur) en cours de préparation : les copies déjà produites sont supprimées. */
    private suspend fun withBatchCleanup(batchId: () -> String?, block: suspend () -> ShareOutcome): ShareOutcome =
        try {
            block()
        } catch (e: Throwable) {
            batchId()?.let { id -> withContext(NonCancellable) { shareableMediaProvider.deleteBatch(id) } }
            throw e
        }

    private companion object {
        const val CONTENT_SCHEME = "content://"
    }
}

/** Réglage « Partager sans métadonnées ». */
class ShareMetadataSettingUseCase(
    private val sharePreferences: SharePreferences,
) {
    fun observe(): Flow<Boolean> = sharePreferences.observeStripMetadata()

    suspend fun set(enabled: Boolean) = sharePreferences.setStripMetadata(enabled)
}
