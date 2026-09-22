package dev.ybdn.ciaocloud.domain.model

/** Décision prise sur un média dans l'écran de tri (spec v4). */
enum class TriageDecision { KEPT, QUEUED_FOR_DELETION, SNOOZED }

/** Décision de tri d'un média, par clé stable (même clé que le favori, voir `FavoriteKeys`). */
data class TriageState(
    val key: String,
    val decision: TriageDecision,
    val decidedAtEpochMillis: Long,
    /** Renseigné uniquement pour [TriageDecision.SNOOZED] : date de réapparition dans la pile. */
    val snoozeUntilEpochMillis: Long?,
)

/** Pile de tri et file d'attente de suppression, calculées sur la chronologie courante. */
data class TriagePile(
    /** Médias à trier, dans l'ordre de présentation. */
    val pending: List<GalleryItem>,
    /** Médias en attente de suppression (toutes sessions confondues). */
    val queued: List<GalleryItem>,
)

/** Compteurs du bilan : décisions de la session en cours, et file d'attente complète. */
data class TriageSummary(
    val kept: Int,
    val queued: Int,
    val queuedBytes: Long,
    val snoozed: Int,
    val totalQueued: Int,
    val totalQueuedBytes: Long,
)

/**
 * Suppressions en attente, rangées selon le mécanisme appliqué : corbeille système pour tout
 * média présent sur le téléphone (sa copie SSD éventuelle est aussi effacée), suppression
 * définitive pour un média présent uniquement sur le SSD.
 */
data class TriageDeletionPlan(
    val phone: List<GalleryItem>,
    val ssdOnly: List<GalleryItem>,
) {
    val all: List<GalleryItem> get() = phone + ssdOnly

    /** Médias du téléphone sans copie sur le SSD. */
    val notBackedUpCount: Int get() = phone.count { it.location == GalleryLocation.PHONE }

    /** Médias du téléphone dont la copie SSD sera aussi effacée, définitivement. */
    val backedUpCount: Int get() = phone.count { it.location == GalleryLocation.BOTH }

    /** Fichiers effacés définitivement du SSD (copies des médias sauvegardés et médias SSD seuls). */
    val ssdDeletionCount: Int get() = backedUpCount + ssdOnly.size

    val totalBytes: Long get() = all.sumOf { it.sizeBytes }

    val isEmpty: Boolean get() = phone.isEmpty() && ssdOnly.isEmpty()
}
