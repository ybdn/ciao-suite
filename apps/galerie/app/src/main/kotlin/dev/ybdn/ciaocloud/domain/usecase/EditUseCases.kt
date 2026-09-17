package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.EditAvailability
import dev.ybdn.ciaocloud.domain.model.EditCapabilities
import dev.ybdn.ciaocloud.domain.model.EditRecipe
import dev.ybdn.ciaocloud.domain.model.EditUnavailableReason
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.MetadataChanges
import dev.ybdn.ciaocloud.domain.model.MetadataEditSummary
import dev.ybdn.ciaocloud.domain.model.SaveEditOutcome
import dev.ybdn.ciaocloud.domain.model.SaveMode
import dev.ybdn.ciaocloud.domain.repository.EditJournal
import dev.ybdn.ciaocloud.domain.repository.EditWorkspace
import dev.ybdn.ciaocloud.domain.repository.PhotoEditRenderer
import dev.ybdn.ciaocloud.domain.repository.MediaWriteAccess
import dev.ybdn.ciaocloud.domain.repository.MetadataWriter
import dev.ybdn.ciaocloud.domain.repository.SsdMediaBrowser
import dev.ybdn.ciaocloud.domain.repository.TransferActivity
import dev.ybdn.ciaocloud.domain.util.EditCapabilitiesPolicy
import dev.ybdn.ciaocloud.domain.util.EditContext
import dev.ybdn.ciaocloud.domain.util.ExifWritePlan
import dev.ybdn.ciaocloud.domain.util.EditedPhotoMetadata
import dev.ybdn.ciaocloud.domain.util.FileNameCollisionResolver
import dev.ybdn.ciaocloud.domain.util.MediaFileTypes
import dev.ybdn.ciaocloud.domain.util.OrientationCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** Actions d'édition disponibles pour un élément, selon son format, son emplacement et l'état de l'app. */
class GetEditCapabilitiesUseCase(
    private val ssdMediaBrowser: SsdMediaBrowser,
    private val transferActivity: TransferActivity,
) {
    val transferRunning: StateFlow<Boolean> get() = transferActivity.isRunning

    operator fun invoke(item: GalleryItem, isExternal: Boolean = false, isLocked: Boolean = false): EditCapabilities =
        EditCapabilitiesPolicy.capabilities(
            EditContext(
                location = item.location,
                mediaType = item.mediaType,
                mimeType = item.mimeType,
                widthPx = item.phone?.width?.takeIf { it > 0 },
                heightPx = item.phone?.height?.takeIf { it > 0 },
                ssdAvailable = ssdMediaBrowser.isAvailable.value,
                transferRunning = transferActivity.isRunning.value,
                isExternal = isExternal,
                isLocked = isLocked,
            ),
        )
}

/**
 * Enregistre une photo retouchée, en copie ou en remplacement de l'original. Une recette limitée aux
 * rotations et au miroir d'un JPEG n'est pas réencodée : seule la balise `Orientation` change, ce
 * qui préserve qualité, Ultra HDR et photo animée. Sinon, la photo est rendue puis réencodée dans le
 * format de l'original (JPEG pour un HEIC/AVIF), avec les métadonnées de la liste blanche B6.
 */
class SavePhotoEditUseCase(
    private val editWorkspace: EditWorkspace,
    private val metadataWriter: MetadataWriter,
    private val originalWorkFiles: OriginalWorkFiles,
    private val photoEditRenderer: PhotoEditRenderer,
    private val safeFileEditor: SafeFileEditor,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) {
    suspend operator fun invoke(
        item: GalleryItem,
        recipe: EditRecipe,
        mode: SaveMode,
        capabilities: EditCapabilities,
    ): SaveEditOutcome {
        if (recipe.isIdentity) return SaveEditOutcome.NothingToSave
        val availability = if (mode == SaveMode.COPY) capabilities.editCopy else capabilities.replace
        if (availability is EditAvailability.Unavailable) return SaveEditOutcome.Unavailable(availability.reason)
        return if (recipe.isOrientationOnly && capabilities.losslessRotation.isAvailable) {
            saveLossless(item, recipe, mode)
        } else {
            saveRendered(item, recipe, mode)
        }
    }

    private suspend fun saveLossless(item: GalleryItem, recipe: EditRecipe, mode: SaveMode): SaveEditOutcome =
        originalWorkFiles.withCopy(item) { workPath ->
            val orientation = OrientationCodec.compose(metadataWriter.readOrientation(workPath), recipe.transform)
            metadataWriter.apply(workPath, ExifWritePlan.orientation(orientation))
            write(item, workPath, mode, targetExtension = null, mimeType = item.mimeType)
        }

    private suspend fun saveRendered(item: GalleryItem, recipe: EditRecipe, mode: SaveMode): SaveEditOutcome {
        val format = EditCapabilitiesPolicy.outputFormat(item.mimeType)
            ?: return SaveEditOutcome.Unavailable(EditUnavailableReason.UNSUPPORTED_FORMAT)
        val originalExtension = FileNameCollisionResolver.splitBaseAndExtension(item.displayName).second
        val keepsExtension = MediaFileTypes.fromFileName(item.displayName)?.mimeType == format.mimeType
        val extension = if (keepsExtension) originalExtension else format.extension
        return originalWorkFiles.withNewFile(item, extension) { sourceUri, workPath ->
            val rendered = photoEditRenderer.render(sourceUri, recipe, format, workPath)
            metadataWriter.copyTags(sourceUri, workPath, EditedPhotoMetadata.COPIED_TAGS)
            metadataWriter.apply(workPath, EditedPhotoMetadata.overrides(rendered.widthPx, rendered.heightPx, clock()))
            write(item, workPath, mode, targetExtension = extension.takeUnless { keepsExtension }, mimeType = format.mimeType)
        }
    }

    private suspend fun write(
        item: GalleryItem,
        workPath: String,
        mode: SaveMode,
        targetExtension: String?,
        mimeType: String,
    ): SaveEditOutcome {
        val fingerprint = editWorkspace.fingerprint(workPath) ?: return SaveEditOutcome.Failed(WORK_FILE_MISSING)
        return when (mode) {
            SaveMode.COPY -> safeFileEditor.createCopy(item, workPath, fingerprint, targetExtension, mimeType)
            SaveMode.REPLACE -> safeFileEditor.replace(item, workPath, fingerprint)
        }
    }

    private companion object {
        const val WORK_FILE_MISSING = "Fichier de travail introuvable"
    }
}

/**
 * Copie de travail de l'original d'un élément (téléphone en priorité, sinon SSD), supprimée après
 * usage quelle que soit l'issue.
 */
class OriginalWorkFiles(
    private val editWorkspace: EditWorkspace,
    private val ssdMediaBrowser: SsdMediaBrowser,
) {
    /** Fichier de travail contenant une copie exacte de l'original. */
    suspend fun withCopy(item: GalleryItem, block: suspend (workPath: String) -> SaveEditOutcome): SaveEditOutcome {
        val extension = FileNameCollisionResolver.splitBaseAndExtension(item.displayName).second.ifEmpty { "jpg" }
        return withNewFile(item, extension) { sourceUri, workPath ->
            editWorkspace.copyOriginal(sourceUri, workPath)
            block(workPath)
        }
    }

    /** Nouveau fichier de travail vide, et URI de l'original à lire. */
    suspend fun withNewFile(
        item: GalleryItem,
        extension: String,
        block: suspend (sourceUri: String, workPath: String) -> SaveEditOutcome,
    ): SaveEditOutcome {
        val sourceUri = item.phone?.uri
            ?: item.ssd?.let { ssdMediaBrowser.documentUri(it.relativePath) }
            ?: return SaveEditOutcome.SsdUnavailable
        val workPath = editWorkspace.newWorkFile(extension.ifEmpty { "jpg" })
        return try {
            block(sourceUri, workPath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SaveEditOutcome.Failed(e.message ?: e::class.simpleName ?: "Erreur inconnue")
        } finally {
            withContext(NonCancellable) { editWorkspace.delete(workPath) }
        }
    }
}

/**
 * Modifie en place les métadonnées d'une ou plusieurs photos (pixels jamais réencodés). Les originaux
 * du téléphone sont autorisés en une seule confirmation système (par lots de 500) avant toute
 * écriture ; un échec sur une photo n'arrête pas les suivantes.
 */
class EditMetadataUseCase(
    private val editWorkspace: EditWorkspace,
    private val metadataWriter: MetadataWriter,
    private val originalWorkFiles: OriginalWorkFiles,
    private val getEditCapabilities: GetEditCapabilitiesUseCase,
    private val writeAccess: MediaWriteAccess,
    private val safeFileEditor: SafeFileEditor,
) {
    suspend operator fun invoke(
        items: List<GalleryItem>,
        changes: MetadataChanges,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): MetadataEditSummary {
        val plan = ExifWritePlan.from(changes)
        val reasons = items.associateWith { (getEditCapabilities(it).metadata as? EditAvailability.Unavailable)?.reason }
        val editable = items.filter { reasons[it] == null }
        if (editable.isEmpty()) {
            return MetadataEditSummary(
                skipped = items.size,
                ssdUnavailable = reasons.values.any { it == EditUnavailableReason.SSD_REQUIRED },
            )
        }
        if (plan.isEmpty) return MetadataEditSummary(skipped = items.size - editable.size)

        for (batch in editable.mapNotNull { it.phone?.uri }.chunked(MAX_URIS_PER_REQUEST)) {
            if (!writeAccess.request(batch)) return MetadataEditSummary(cancelled = true)
        }

        var modified = 0
        val failed = ArrayList<String>()
        editable.forEachIndexed { index, item ->
            onProgress(index + 1, editable.size)
            val outcome = originalWorkFiles.withCopy(item) { workPath ->
                metadataWriter.apply(workPath, plan)
                val fingerprint = editWorkspace.fingerprint(workPath)
                    ?: return@withCopy SaveEditOutcome.Failed("Fichier de travail introuvable")
                safeFileEditor.replace(item, workPath, fingerprint, accessGranted = true)
            }
            if (outcome == SaveEditOutcome.Replaced) modified++ else failed += item.displayName
        }
        return MetadataEditSummary(modified = modified, skipped = items.size - editable.size, failedNames = failed)
    }

    private companion object {
        const val MAX_URIS_PER_REQUEST = 500
    }
}

/**
 * Reprise des écritures interrompues (app tuée pendant un enregistrement), au lancement de l'app et
 * avant toute réindexation du SSD. Une opération sur le SSD débranché reste au journal.
 */
class RecoverInterruptedEditsUseCase(
    private val editJournal: EditJournal,
    private val safeFileEditor: SafeFileEditor,
) {
    /** @return le nombre d'opérations encore en attente. */
    suspend operator fun invoke(): Int =
        editJournal.entries().count { entry ->
            val recovered = try {
                safeFileEditor.recover(entry)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            !recovered
        }
}
