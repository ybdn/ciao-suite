package dev.ybdn.ciao.clavier.domain.clipboard

/** Élément de l'historique du presse-papiers. [copiedAt] : horloge murale, en millisecondes. */
data class ClipboardItem(val id: Long, val text: String, val copiedAt: Long, val pinned: Boolean)

/** Durée de conservation des éléments non épinglés (apps/clavier/docs/spec-v1.md §9). */
enum class ClipboardRetention(val millis: Long) {
    OneHour(60 * 60 * 1000L),
    OneDay(24 * 60 * 60 * 1000L),
    SevenDays(7 * 24 * 60 * 60 * 1000L),
}

data class ClipboardSettings(
    val historyEnabled: Boolean = true,
    val retention: ClipboardRetention = ClipboardRetention.OneHour,
)

/**
 * Règles de l'historique du presse-papiers (apps/clavier/docs/spec-v1.md §9). Kotlin pur : le
 * stockage (Room) et l'écoute du système (`ClipboardManager`) les appliquent.
 */
object ClipboardRules {

    /** Nombre maximal d'éléments ; au-delà, les plus anciens non épinglés partent. */
    const val MaxItems = 25

    /** Durée pendant laquelle la puce « Coller » propose la dernière copie. */
    const val PasteChipMillis = 60 * 1000L

    /** Au-delà, un texte n'est pas retenu (copie d'un document entier). */
    const val MaxTextLength = 10_000

    /** Texte seulement, non vide, pas marqué sensible (gestionnaires de mots de passe…). */
    fun accepts(text: CharSequence?, isSensitive: Boolean): Boolean =
        !isSensitive && text != null && text.isNotBlank() && text.length <= MaxTextLength

    fun isExpired(item: ClipboardItem, now: Long, retention: ClipboardRetention): Boolean =
        !item.pinned && now - item.copiedAt > retention.millis

    /** Éléments à supprimer : expirés, puis les plus anciens non épinglés au-delà de [MaxItems]. */
    fun toRemove(items: List<ClipboardItem>, now: Long, retention: ClipboardRetention): List<ClipboardItem> {
        val (expired, kept) = items.partition { isExpired(it, now, retention) }
        val overflow = (kept.size - MaxItems).coerceAtLeast(0)
        val oldestUnpinned = kept.filterNot { it.pinned }.sortedBy { it.copiedAt }.take(overflow)
        return expired + oldestUnpinned
    }

    /** Ordre d'affichage : les épinglés d'abord, puis du plus récent au plus ancien. */
    fun displayed(items: List<ClipboardItem>, now: Long, retention: ClipboardRetention): List<ClipboardItem> =
        items.filterNot { isExpired(it, now, retention) }
            .sortedWith(compareByDescending<ClipboardItem> { it.pinned }.thenByDescending { it.copiedAt })

    /** Dernière copie à proposer dans la puce « Coller », si elle date de moins d'une minute. */
    fun pasteChip(items: List<ClipboardItem>, now: Long): ClipboardItem? =
        items.maxByOrNull { it.copiedAt }?.takeIf { now - it.copiedAt in 0..PasteChipMillis }
}
