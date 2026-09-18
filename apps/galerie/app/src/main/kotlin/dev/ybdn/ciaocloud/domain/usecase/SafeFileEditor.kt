package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.FavoriteKeys
import dev.ybdn.ciaocloud.domain.model.FileFingerprint
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.SaveEditOutcome
import dev.ybdn.ciaocloud.domain.model.SsdMedia
import dev.ybdn.ciaocloud.domain.repository.FavoritesRepository
import dev.ybdn.ciaocloud.domain.repository.SsdMoveStep
import dev.ybdn.ciaocloud.domain.util.DestinationDecision
import dev.ybdn.ciaocloud.domain.util.DuplicateResolver
import dev.ybdn.ciaocloud.domain.util.TimelineBuilder
import dev.ybdn.ciaocloud.domain.model.TransferRecord
import dev.ybdn.ciaocloud.domain.repository.EditJournal
import dev.ybdn.ciaocloud.domain.repository.EditJournalEntry
import dev.ybdn.ciaocloud.domain.repository.EditStepKind
import dev.ybdn.ciaocloud.domain.repository.MediaWriteAccess
import dev.ybdn.ciaocloud.domain.repository.PhoneEditStep
import dev.ybdn.ciaocloud.domain.repository.PhoneMediaWriter
import dev.ybdn.ciaocloud.domain.repository.SsdEditStep
import dev.ybdn.ciaocloud.domain.repository.SsdMediaIndex
import dev.ybdn.ciaocloud.domain.repository.SsdMediaWriter
import dev.ybdn.ciaocloud.domain.repository.SsdThumbnailCache
import dev.ybdn.ciaocloud.domain.repository.TransactionRunner
import dev.ybdn.ciaocloud.domain.repository.TransferStateRepository
import dev.ybdn.ciaocloud.domain.repository.TriageRepository
import dev.ybdn.ciaocloud.domain.util.EditedFileNamer
import dev.ybdn.ciaocloud.domain.util.MediaFileTypes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Écriture sûre des fichiers édités (spec v3 A1/A4), commune à la retouche, aux métadonnées et à la
 * reprise après interruption.
 *
 * Invariant A1 : avant de modifier un original, tous les enregistrements de transfert qui le
 * concernent (le média, et tout autre média vérifié vers la même copie SSD) sont retirés. Une fois
 * l'écriture terminée, l'état réel des fichiers est relu : enregistrements restaurés si rien n'a
 * changé, mis à jour si le téléphone et le SSD portent tous deux le nouveau contenu, supprimés sinon.
 * Une écriture commencée n'est jamais interrompue par une annulation : elle se termine, ou reste
 * journalisée pour être reprise.
 */
class SafeFileEditor(
    private val phoneWriter: PhoneMediaWriter,
    private val ssdWriter: SsdMediaWriter,
    private val writeAccess: MediaWriteAccess,
    private val journal: EditJournal,
    private val transferStateRepository: TransferStateRepository,
    private val ssdMediaIndex: SsdMediaIndex,
    private val ssdThumbnailCache: SsdThumbnailCache,
    private val favoritesRepository: FavoritesRepository,
    private val triageRepository: TriageRepository,
    private val transactionRunner: TransactionRunner,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Opérations de ce processus en cours d'écriture : la reprise ne doit pas y toucher. */
    private val activeIds = HashSet<String>()
    private val activeIdsMutex = Mutex()

    /**
     * Remplace le contenu de l'élément (téléphone, SSD ou les deux) par le fichier de travail [workPath].
     * Ordre : SSD vérifié branché, droit d'écriture téléphone (un refus annule tout), SSD, téléphone.
     *
     * @param accessGranted droit d'écriture déjà obtenu (modification groupée).
     */
    suspend fun replace(
        item: GalleryItem,
        workPath: String,
        expected: FileFingerprint,
        accessGranted: Boolean = false,
    ): SaveEditOutcome {
        val phone = item.phone
        val ssd = item.ssd
        if (ssd != null && !ssdWriter.isAvailable()) return SaveEditOutcome.SsdUnavailable
        if (phone != null && !accessGranted && !writeAccess.request(listOf(phone.uri))) return SaveEditOutcome.Cancelled

        return withContext(NonCancellable) {
            val phoneOriginal = phone?.let {
                phoneWriter.fingerprint(it.uri) ?: return@withContext SaveEditOutcome.Failed(ORIGINAL_MISSING_PHONE)
            }
            val ssdOriginal = ssd?.let {
                ssdWriter.fingerprint(it.relativePath) ?: return@withContext SaveEditOutcome.Failed(ORIGINAL_MISSING_SSD)
            }
            val previousRecords = buildList {
                phone?.let { addAll(transferStateRepository.getByMediaStoreIds(listOf(it.mediaStoreId))) }
                ssd?.let { addAll(transferStateRepository.getByDestinationPath(it.relativePath)) }
            }.distinctBy { it.mediaStoreId }

            val id = newId()
            val entry = EditJournalEntry(
                id = id,
                phone = phone?.let {
                    PhoneEditStep(EditStepKind.REPLACE, it.uri, expected, phoneOriginal!!.checksum, journal.backupPath(id))
                },
                ssd = ssd?.let { SsdEditStep(EditStepKind.REPLACE, it.relativePath, expected, ssdOriginal!!.checksum) },
                previousRecords = previousRecords,
                editedMediaStoreId = phone?.mediaStoreId?.takeIf { ssd != null },
            )
            runJournaled(entry) {
                transactionRunner.inTransaction {
                    previousRecords.forEach { transferStateRepository.delete(it.mediaStoreId) }
                }
                val failure = try {
                    ssd?.let { replaceOnSsd(it.relativePath, workPath, expected) }
                    phone?.let { phoneWriter.replace(it.uri, workPath, expected, entry.phone!!.backupPath!!) }
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.message ?: e::class.simpleName ?: UNKNOWN_ERROR
                }
                settle(entry)
                val complete = !hasPendingBackup(entry) && !hasSsdLeftovers(entry)
                val outcome = if (failure == null) SaveEditOutcome.Replaced else SaveEditOutcome.Failed(failure)
                outcome to complete
            }
        }
    }

    /**
     * Crée une copie à côté de l'original : sur le téléphone pour un élément du téléphone (même dossier),
     * sinon sur le SSD (même dossier jour). La copie n'hérite d'aucun enregistrement de transfert.
     *
     * @param targetExtension extension de la copie si elle change de format, sinon null.
     */
    suspend fun createCopy(
        item: GalleryItem,
        workPath: String,
        expected: FileFingerprint,
        targetExtension: String?,
        mimeType: String,
    ): SaveEditOutcome {
        val phone = item.phone
        if (phone != null) {
            val directory = phone.relativePath ?: DEFAULT_PHONE_DIRECTORY
            val name = EditedFileNamer.copyName(phone.displayName, targetExtension, phoneWriter.existingNames(directory))
            val step = PhoneEditStep(EditStepKind.COPY, uri = null, newFingerprint = expected)
            val entry = EditJournalEntry(newId(), phone = step)
            return withContext(NonCancellable) {
                runJournaled(entry) {
                    var uri: String? = null
                    val outcome = try {
                        uri = phoneWriter.createPending(directory, name, mimeType, phone.takenAtEpochMillis)
                        journal.write(entry.copy(phone = step.copy(uri = uri)))
                        phoneWriter.writePending(uri, workPath, expected)
                        SaveEditOutcome.Copied(FavoriteKeys.phone(phoneWriter.publish(uri)))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        uri?.let { runCatching { phoneWriter.deleteIfPending(it) } }
                        SaveEditOutcome.Failed(e.message ?: UNKNOWN_ERROR)
                    }
                    outcome to true
                }
            }
        }

        val ssd = item.ssd ?: return SaveEditOutcome.Failed(UNKNOWN_ERROR)
        if (!ssdWriter.isAvailable()) return SaveEditOutcome.SsdUnavailable
        val directory = ssd.relativePath.substringBeforeLast('/')
        val names = ssdWriter.listNames(directory) ?: return SaveEditOutcome.SsdUnavailable
        val name = EditedFileNamer.copyName(ssd.displayName, targetExtension, names)
        val finalPath = "$directory/$name"
        val entry = EditJournalEntry(newId(), ssd = SsdEditStep(EditStepKind.COPY, finalPath, expected))
        return withContext(NonCancellable) {
            runJournaled(entry) {
                val temporaryPath = finalPath + NEW_SUFFIX
                val outcome = try {
                    ssdWriter.write(temporaryPath, workPath, expected)
                    ssdWriter.rename(temporaryPath, name)
                    val type = MediaFileTypes.fromFileName(name)
                    ssdMediaIndex.upsert(
                        ssd.copy(
                            relativePath = finalPath,
                            displayName = name,
                            mimeType = type?.mimeType ?: mimeType,
                            sizeBytes = expected.sizeBytes,
                            lastModifiedEpochMillis = clock(),
                        ),
                    )
                    SaveEditOutcome.Copied(FavoriteKeys.ssd(finalPath))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    runCatching { ssdWriter.delete(temporaryPath) }
                    SaveEditOutcome.Failed(e.message ?: UNKNOWN_ERROR)
                }
                outcome to true
            }
        }
    }

    /**
     * Range un fichier du SSD dans le dossier jour [newDir] après changement de sa date (spec v3 C7) :
     * dossier existant réutilisé quelle que soit sa casse, doublon identique déjà présent → source
     * supprimée et références redirigées, sinon premier nom libre. Index, enregistrements de transfert,
     * favori et vignette suivent le fichier, en une transaction. Un dossier devenu vide est conservé.
     */
    suspend fun relocate(ssd: SsdMedia, newDir: String, capturedAtEpochMillis: Long?): SaveEditOutcome {
        if (!ssdWriter.isAvailable()) return SaveEditOutcome.SsdUnavailable
        return withContext(NonCancellable) {
            val targetDir = ssdWriter.resolveDirectory(newDir, create = true)
                ?: return@withContext SaveEditOutcome.Failed(SSD_DIRECTORY_UNAVAILABLE)
            val source = ssd.relativePath
            val size = ssdWriter.fingerprint(source)?.sizeBytes ?: return@withContext SaveEditOutcome.Failed(ORIGINAL_MISSING_SSD)
            val entries = ssdWriter.listEntries(targetDir) ?: return@withContext SaveEditOutcome.Failed(SSD_DIRECTORY_UNAVAILABLE)
            val decision = DuplicateResolver.resolve(ssd.displayName, size, entries) { existingName ->
                if (ssdWriter.sameContent(source, "$targetDir/$existingName")) existingName else null
            }
            val step = when (decision) {
                is DestinationDecision.AlreadyPresent ->
                    SsdMoveStep(source, "$targetDir/${decision.existingFileName}", duplicate = true, capturedAtEpochMillis)
                is DestinationDecision.CopyAs ->
                    SsdMoveStep(source, "$targetDir/${decision.fileName}", duplicate = false, capturedAtEpochMillis)
            }
            runJournaled(EditJournalEntry(newId(), move = step)) {
                val outcome = try {
                    if (step.duplicate) {
                        ssdWriter.delete(step.fromPath)
                    } else {
                        ssdWriter.move(step.fromPath, targetDir, step.toPath.substringAfterLast('/'))
                    }
                    applyRelocation(step)
                    SaveEditOutcome.Copied(FavoriteKeys.ssd(step.toPath))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SaveEditOutcome.Failed(e.message ?: UNKNOWN_ERROR)
                }
                outcome to true
            }
        }
    }

    /** Date de prise de vue modifiée sans changement de dossier : l'index suit le nouvel instant. */
    suspend fun updateCapturedAt(relativePath: String, capturedAtEpochMillis: Long?) {
        ssdMediaIndex.get(relativePath)?.let { ssdMediaIndex.upsert(it.copy(capturedAtEpochMillis = capturedAtEpochMillis)) }
    }

    /**
     * Reprend une opération interrompue (app tuée pendant l'écriture). @return false si elle doit être
     * retentée plus tard (SSD débranché, restauration impossible) : l'entrée reste alors au journal.
     */
    suspend fun recover(entry: EditJournalEntry): Boolean = withContext(NonCancellable) {
        val isActive = activeIdsMutex.withLock { entry.id in activeIds }
        if (isActive) return@withContext false
        val ssdStep = entry.ssd
        if ((ssdStep != null || entry.move != null) && !ssdWriter.isAvailable()) return@withContext false
        entry.move?.let { recoverMove(it) }

        when (ssdStep?.kind) {
            EditStepKind.REPLACE -> recoverSsdReplace(ssdStep)
            EditStepKind.COPY -> ssdWriter.delete(ssdStep.relativePath + NEW_SUFFIX)
            null -> Unit
        }
        val phoneStep = entry.phone
        when (phoneStep?.kind) {
            EditStepKind.COPY -> phoneStep.uri?.let { phoneWriter.deleteIfPending(it) }
            EditStepKind.REPLACE -> {
                val backup = phoneStep.backupPath!!
                if (phoneWriter.backupExists(backup)) {
                    val current = phoneWriter.fingerprint(phoneStep.uri!!)?.checksum
                    if (current != phoneStep.originalChecksum && current != phoneStep.newFingerprint.checksum) {
                        try {
                            phoneWriter.restoreFromBackup(phoneStep.uri, backup)
                        } catch (e: Exception) {
                            return@withContext false
                        }
                    }
                    phoneWriter.deleteBackup(backup)
                }
            }
            null -> Unit
        }
        settle(entry)
        journal.remove(entry.id)
        true
    }

    /** Déplacement interrompu : terminé s'il a eu lieu (références mises à jour), sinon abandonné. */
    private suspend fun recoverMove(step: SsdMoveStep) {
        ssdWriter.delete(step.toPath + NEW_SUFFIX)
        val sourceExists = ssdWriter.exists(step.fromPath)
        val targetExists = ssdWriter.exists(step.toPath)
        if (!targetExists) return
        if (sourceExists) {
            // Cible complète (renommée après vérification) ou doublon identique : la source est en trop.
            if (!step.duplicate || ssdWriter.sameContent(step.fromPath, step.toPath)) {
                ssdWriter.delete(step.fromPath)
            } else {
                return
            }
        }
        applyRelocation(step)
    }

    /** Index, enregistrements de transfert, favori, décision de tri et vignettes suivent le fichier déplacé (idempotent). */
    private suspend fun applyRelocation(step: SsdMoveStep) {
        val newName = step.toPath.substringAfterLast('/')
        transactionRunner.inTransaction {
            val previous = ssdMediaIndex.get(step.fromPath)
            ssdMediaIndex.remove(step.fromPath)
            val day = TimelineBuilder.parseDayFromPath(step.toPath)
            val existing = ssdMediaIndex.get(step.toPath)
            if (day != null && (previous != null || existing != null)) {
                val base = existing ?: previous!!
                ssdMediaIndex.upsert(
                    base.copy(
                        relativePath = step.toPath,
                        displayName = newName,
                        captureDate = day,
                        capturedAtEpochMillis = step.capturedAtEpochMillis ?: base.capturedAtEpochMillis,
                    ),
                )
            }
            transferStateRepository.getByDestinationPath(step.fromPath).forEach {
                transferStateRepository.upsert(it.copy(destinationPath = step.toPath))
            }
            favoritesRepository.renameKey(FavoriteKeys.ssd(step.fromPath), FavoriteKeys.ssd(step.toPath))
            triageRepository.renameKey(FavoriteKeys.ssd(step.fromPath), FavoriteKeys.ssd(step.toPath))
        }
        ssdThumbnailCache.remove(step.fromPath)
        ssdThumbnailCache.remove(step.toPath)
    }

    /** Exécute [block] (résultat, opération terminée) avec l'entrée au journal ; elle n'en sort que si terminée. */
    private suspend fun runJournaled(
        entry: EditJournalEntry,
        block: suspend () -> Pair<SaveEditOutcome, Boolean>,
    ): SaveEditOutcome {
        activeIdsMutex.withLock { activeIds += entry.id }
        try {
            journal.write(entry)
            val (outcome, complete) = block()
            if (complete) journal.remove(entry.id)
            return outcome
        } finally {
            activeIdsMutex.withLock { activeIds -= entry.id }
        }
    }

    /** `<nom>.ciao-new` écrit et vérifié → original renommé en `<nom>.ciao-old` → `<nom>.ciao-new` renommé → ancien supprimé. */
    private suspend fun replaceOnSsd(relativePath: String, workPath: String, expected: FileFingerprint) {
        val name = relativePath.substringAfterLast('/')
        val newPath = relativePath + NEW_SUFFIX
        val oldPath = relativePath + OLD_SUFFIX
        ssdWriter.delete(newPath)
        if (ssdWriter.exists(oldPath)) ssdWriter.delete(oldPath)
        ssdWriter.write(newPath, workPath, expected)
        try {
            ssdWriter.rename(relativePath, name + OLD_SUFFIX)
        } catch (e: Exception) {
            ssdWriter.delete(newPath)
            throw e
        }
        try {
            ssdWriter.rename(newPath, name)
        } catch (e: Exception) {
            runCatching { ssdWriter.rename(oldPath, name) }
            ssdWriter.delete(newPath)
            throw e
        }
        ssdWriter.delete(oldPath)
    }

    private suspend fun recoverSsdReplace(step: SsdEditStep) {
        val path = step.relativePath
        val name = path.substringAfterLast('/')
        val oldPath = path + OLD_SUFFIX
        if (ssdWriter.exists(oldPath)) {
            val finalChecksum = if (ssdWriter.exists(path)) ssdWriter.fingerprint(path)?.checksum else null
            if (finalChecksum == step.newFingerprint.checksum) {
                ssdWriter.delete(oldPath)
            } else {
                if (finalChecksum != null) ssdWriter.delete(path)
                ssdWriter.rename(oldPath, name)
            }
        }
        ssdWriter.delete(path + NEW_SUFFIX)
    }

    /** Relit l'état réel des fichiers remplacés et en déduit les enregistrements de transfert (invariant A1). */
    private suspend fun settle(entry: EditJournalEntry) {
        val phoneStep = entry.phone?.takeIf { it.kind == EditStepKind.REPLACE }
        val ssdStep = entry.ssd?.takeIf { it.kind == EditStepKind.REPLACE }
        if (phoneStep == null && ssdStep == null) return
        val phoneNow = phoneStep?.let { phoneWriter.fingerprint(it.uri!!)?.checksum }
        val ssdNow = ssdStep?.let { ssdWriter.fingerprint(it.relativePath)?.checksum }

        val unchanged = (phoneStep == null || phoneNow == phoneStep.originalChecksum) &&
            (ssdStep == null || ssdNow == ssdStep.originalChecksum)
        val bothUpdated = phoneStep != null && ssdStep != null &&
            phoneNow == phoneStep.newFingerprint.checksum && ssdNow == ssdStep.newFingerprint.checksum

        transactionRunner.inTransaction {
            when {
                unchanged -> entry.previousRecords.forEach { transferStateRepository.upsert(it) }
                bothUpdated -> entry.previousRecords
                    .firstOrNull { it.mediaStoreId == entry.editedMediaStoreId }
                    ?.let { transferStateRepository.upsert(it.updatedTo(ssdStep!!.newFingerprint)) }
                // Contenus différents entre téléphone et SSD : plus rien n'est « vérifié ».
                else -> Unit
            }
        }
        if (ssdStep != null && ssdNow == ssdStep.newFingerprint.checksum) {
            ssdMediaIndex.get(ssdStep.relativePath)?.let {
                ssdMediaIndex.upsert(it.copy(sizeBytes = ssdStep.newFingerprint.sizeBytes, lastModifiedEpochMillis = clock()))
            }
            ssdThumbnailCache.remove(ssdStep.relativePath)
        }
    }

    private suspend fun hasPendingBackup(entry: EditJournalEntry): Boolean {
        val backup = entry.phone?.takeIf { it.kind == EditStepKind.REPLACE }?.backupPath ?: return false
        return phoneWriter.backupExists(backup)
    }

    /** Fichier intermédiaire resté sur le SSD (échec de la remise en place) : l'opération reste à reprendre. */
    private suspend fun hasSsdLeftovers(entry: EditJournalEntry): Boolean {
        val path = entry.ssd?.takeIf { it.kind == EditStepKind.REPLACE }?.relativePath ?: return false
        return runCatching { ssdWriter.exists(path + OLD_SUFFIX) || ssdWriter.exists(path + NEW_SUFFIX) }.getOrDefault(true)
    }

    private fun TransferRecord.updatedTo(fingerprint: FileFingerprint) =
        copy(checksum = fingerprint.checksum, sizeBytes = fingerprint.sizeBytes)

    companion object {
        const val NEW_SUFFIX = ".ciao-new"
        const val OLD_SUFFIX = ".ciao-old"
        private const val DEFAULT_PHONE_DIRECTORY = "Pictures/"
        private const val UNKNOWN_ERROR = "Erreur inconnue"
        private const val ORIGINAL_MISSING_PHONE = "Original introuvable sur le téléphone"
        private const val ORIGINAL_MISSING_SSD = "Original introuvable sur le SSD"
        private const val SSD_DIRECTORY_UNAVAILABLE = "Dossier inaccessible sur le SSD"
    }
}
