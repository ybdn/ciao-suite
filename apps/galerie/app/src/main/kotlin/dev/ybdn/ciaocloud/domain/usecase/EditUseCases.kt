package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.EditAvailability
import dev.ybdn.ciaocloud.domain.model.EditCapabilities
import dev.ybdn.ciaocloud.domain.model.EditRecipe
import dev.ybdn.ciaocloud.domain.model.EditUnavailableReason
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.SaveEditOutcome
import dev.ybdn.ciaocloud.domain.model.SaveMode
import dev.ybdn.ciaocloud.domain.repository.EditJournal
import dev.ybdn.ciaocloud.domain.repository.EditWorkspace
import dev.ybdn.ciaocloud.domain.repository.MetadataWriter
import dev.ybdn.ciaocloud.domain.repository.SsdMediaBrowser
import dev.ybdn.ciaocloud.domain.repository.TransferActivity
import dev.ybdn.ciaocloud.domain.util.EditCapabilitiesPolicy
import dev.ybdn.ciaocloud.domain.util.EditContext
import dev.ybdn.ciaocloud.domain.util.ExifWritePlan
import dev.ybdn.ciaocloud.domain.util.FileNameCollisionResolver
import dev.ybdn.ciaocloud.domain.util.OrientationCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

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
 * qui préserve qualité, Ultra HDR et photo animée.
 */
class SavePhotoEditUseCase(
    private val editWorkspace: EditWorkspace,
    private val metadataWriter: MetadataWriter,
    private val ssdMediaBrowser: SsdMediaBrowser,
    private val safeFileEditor: SafeFileEditor,
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
        val lossless = capabilities.losslessRotation
        if (!recipe.isOrientationOnly || lossless is EditAvailability.Unavailable) {
            val reason = (lossless as? EditAvailability.Unavailable)?.reason ?: EditUnavailableReason.UNSUPPORTED_FORMAT
            return SaveEditOutcome.Unavailable(reason)
        }

        val sourceUri = item.phone?.uri
            ?: item.ssd?.let { ssdMediaBrowser.documentUri(it.relativePath) }
            ?: return SaveEditOutcome.SsdUnavailable
        val extension = FileNameCollisionResolver.splitBaseAndExtension(item.displayName).second.ifEmpty { "jpg" }
        val workPath = editWorkspace.newWorkFile(extension)
        return try {
            editWorkspace.copyOriginal(sourceUri, workPath)
            val orientation = OrientationCodec.compose(metadataWriter.readOrientation(workPath), recipe.transform)
            metadataWriter.apply(workPath, ExifWritePlan.orientation(orientation))
            val fingerprint = editWorkspace.fingerprint(workPath) ?: return SaveEditOutcome.Failed(WORK_FILE_MISSING)
            when (mode) {
                SaveMode.COPY -> safeFileEditor.createCopy(item, workPath, fingerprint, targetExtension = null, item.mimeType)
                SaveMode.REPLACE -> safeFileEditor.replace(item, workPath, fingerprint)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SaveEditOutcome.Failed(e.message ?: e::class.simpleName ?: WORK_FILE_MISSING)
        } finally {
            withContext(NonCancellable) { editWorkspace.delete(workPath) }
        }
    }

    private companion object {
        const val WORK_FILE_MISSING = "Fichier de travail introuvable"
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
