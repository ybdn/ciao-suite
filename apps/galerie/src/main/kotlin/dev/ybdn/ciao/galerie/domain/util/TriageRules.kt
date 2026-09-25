package dev.ybdn.ciao.galerie.domain.util

import dev.ybdn.ciao.galerie.domain.model.GalleryItem
import dev.ybdn.ciao.galerie.domain.model.TriageDecision
import dev.ybdn.ciao.galerie.domain.model.TriageDeletionPlan
import dev.ybdn.ciao.galerie.domain.model.TriagePile
import dev.ybdn.ciao.galerie.domain.model.TriageState
import dev.ybdn.ciao.galerie.domain.model.TriageSummary
import java.util.concurrent.TimeUnit

/**
 * Règles pures du tri (spec v4) : pas de curseur ni de session persistés, la pile est toujours
 * la chronologie privée des médias déjà tranchés. Un média jamais trié, ou dont le « plus tard »
 * a expiré, y revient naturellement.
 */
object TriageRules {

    /** Durée fixe du « revoir plus tard » (v1, cf. points ouverts de la spec). */
    val SNOOZE_DURATION_MILLIS: Long = TimeUnit.DAYS.toMillis(7)

    fun snoozeUntil(nowEpochMillis: Long): Long = nowEpochMillis + SNOOZE_DURATION_MILLIS

    /** Le média doit-il être proposé au tri ? */
    fun isPending(state: TriageState?, nowEpochMillis: Long): Boolean = when (state?.decision) {
        null -> true
        TriageDecision.KEPT, TriageDecision.QUEUED_FOR_DELETION -> false
        TriageDecision.SNOOZED -> (state.snoozeUntilEpochMillis ?: 0L) <= nowEpochMillis
    }

    /**
     * Pile groupée par jour, du plus récent au plus ancien, puis par instant de prise de vue dans
     * la journée (même ordre que la grille), et file d'attente de suppression.
     */
    fun pile(items: List<GalleryItem>, states: List<TriageState>, nowEpochMillis: Long): TriagePile {
        val byKey = states.associateBy { it.key }
        val pending = ArrayList<GalleryItem>()
        val queued = ArrayList<GalleryItem>()
        for (item in items) {
            val state = byKey[item.triageKey]
            if (state?.decision == TriageDecision.QUEUED_FOR_DELETION) queued += item
            if (isPending(state, nowEpochMillis)) pending += item
        }
        pending.sortWith(TimelineBuilder.TIMELINE_ORDER)
        queued.sortWith(TimelineBuilder.TIMELINE_ORDER)
        return TriagePile(pending, queued)
    }

    /** Compteurs du bilan : décisions prises depuis [sessionStartEpochMillis], et file d'attente complète. */
    fun summary(items: List<GalleryItem>, states: List<TriageState>, sessionStartEpochMillis: Long): TriageSummary {
        val sizeByKey = items.associate { it.triageKey to it.sizeBytes }
        var kept = 0
        var queued = 0
        var queuedBytes = 0L
        var snoozed = 0
        var totalQueued = 0
        var totalQueuedBytes = 0L
        for (state in states) {
            // Décision d'un média qui n'existe plus (supprimé depuis la galerie) : ignorée.
            val size = sizeByKey[state.key] ?: continue
            val inSession = state.decidedAtEpochMillis >= sessionStartEpochMillis
            when (state.decision) {
                TriageDecision.KEPT -> if (inSession) kept++
                TriageDecision.SNOOZED -> if (inSession) snoozed++
                TriageDecision.QUEUED_FOR_DELETION -> {
                    totalQueued++
                    totalQueuedBytes += size
                    if (inSession) {
                        queued++
                        queuedBytes += size
                    }
                }
            }
        }
        return TriageSummary(kept, queued, queuedBytes, snoozed, totalQueued, totalQueuedBytes)
    }

    /** Un média présent sur le téléphone (sauvegardé ou non) part à la corbeille ; un média SSD seul est effacé. */
    fun deletionPlan(queued: List<GalleryItem>): TriageDeletionPlan {
        val (phone, ssdOnly) = queued.partition { it.phone != null }
        return TriageDeletionPlan(phone = phone, ssdOnly = ssdOnly)
    }
}
