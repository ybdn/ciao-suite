package dev.ybdn.ciao.galerie.domain.usecase

import dev.ybdn.ciao.galerie.domain.model.GalleryItem
import dev.ybdn.ciao.galerie.domain.model.TriageDecision
import dev.ybdn.ciao.galerie.domain.model.TriageDeletionPlan
import dev.ybdn.ciao.galerie.domain.model.TriagePile
import dev.ybdn.ciao.galerie.domain.model.TriageState
import dev.ybdn.ciao.galerie.domain.model.TriageSummary
import dev.ybdn.ciao.galerie.domain.repository.TriageRepository
import dev.ybdn.ciao.galerie.domain.util.TriageRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Pile de tri (chronologie unifiée privée des médias déjà tranchés) et file d'attente de
 * suppression. Recalculée à chaque changement de la chronologie ou des décisions : un nouveau
 * média ou un « plus tard » expiré y apparaît sans état de session.
 */
class ObserveTriagePileUseCase(
    private val observeTimelineUseCase: ObserveTimelineUseCase,
    private val triageRepository: TriageRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    operator fun invoke(): Flow<TriagePile> =
        combine(observeTimelineUseCase(), triageRepository.observeAll()) { items, states ->
            TriageRules.pile(items, states, clock())
        }
}

/** Enregistre la décision prise sur un média ; renvoie la clé écrite (pour l'annulation). */
class RecordTriageDecisionUseCase(
    private val triageRepository: TriageRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(item: GalleryItem, decision: TriageDecision): String {
        val now = clock()
        val state = TriageState(
            key = item.triageKey,
            decision = decision,
            decidedAtEpochMillis = now,
            snoozeUntilEpochMillis = if (decision == TriageDecision.SNOOZED) TriageRules.snoozeUntil(now) else null,
        )
        triageRepository.upsert(state)
        return state.key
    }
}

/**
 * Annule la dernière décision : sa ligne est supprimée, le média redevient à trier. La clé est
 * mémorisée par l'écran, jamais persistée (un seul niveau d'annulation).
 */
class UndoLastTriageDecisionUseCase(
    private val triageRepository: TriageRepository,
) {
    suspend operator fun invoke(key: String) = triageRepository.delete(listOf(key))
}

/** Compteurs du bilan de la session commencée à [sessionStartEpochMillis]. */
class GetTriageSummaryUseCase(
    private val observeTimelineUseCase: ObserveTimelineUseCase,
    private val triageRepository: TriageRepository,
) {
    operator fun invoke(sessionStartEpochMillis: Long): Flow<TriageSummary> =
        combine(observeTimelineUseCase(), triageRepository.observeAll()) { items, states ->
            TriageRules.summary(items, states, sessionStartEpochMillis)
        }
}

/** Réinitialisation depuis les réglages : « gardé » et « plus tard » ; la file de suppression est conservée. */
class ResetTriageUseCase(
    private val triageRepository: TriageRepository,
) {
    suspend operator fun invoke() =
        triageRepository.deleteByDecisions(setOf(TriageDecision.KEPT, TriageDecision.SNOOZED))
}

/** File d'attente de suppression rangée par mécanisme (corbeille du téléphone ou effacement du SSD). */
class ObserveTriageDeletionQueueUseCase(
    private val observeTriagePileUseCase: ObserveTriagePileUseCase,
) {
    operator fun invoke(): Flow<TriageDeletionPlan> =
        observeTriagePileUseCase().map { TriageRules.deletionPlan(it.queued) }
}

/** Retire des médias de la file d'attente : ils redeviennent proposés au tri. */
class DequeueTriageDeletionUseCase(
    private val triageRepository: TriageRepository,
) {
    suspend operator fun invoke(items: List<GalleryItem>) = triageRepository.delete(items.map { it.triageKey })
}

/**
 * Adaptateur de la file d'attente vers la suppression de la galerie (aucune logique de suppression
 * propre) : tout média est supprimé partout. Seules les décisions des médias effectivement
 * supprimés sont retirées ; en cas de refus partiel, les autres restent en file d'attente.
 */
class DeleteQueuedTriageItemsUseCase(
    private val deleteGalleryItemsUseCase: DeleteGalleryItemsUseCase,
    private val triageRepository: TriageRepository,
) {
    suspend operator fun invoke(items: List<GalleryItem>): DeleteItemsOutcome {
        val outcome = deleteGalleryItemsUseCase(items, DeleteTarget.EVERYWHERE)
        if (outcome is DeleteItemsOutcome.Done) {
            val deletedKeys = items.filter { it.key in outcome.completedKeys }.map { it.triageKey }
            if (deletedKeys.isNotEmpty()) triageRepository.delete(deletedKeys)
        }
        return outcome
    }
}
