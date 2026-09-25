package dev.ybdn.ciao.clavier.domain.input

/** Réglages de frappe (apps/clavier/docs/spec-v1.md §6.1, §6.3, §10), avec leurs valeurs par défaut. */
data class TypingSettings(
    val vibration: Boolean = true,
    val soundEnabled: Boolean = false,
    val keyPreview: Boolean = true,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val nonBreakingSpace: Boolean = false,
    val suggestions: Boolean = true,
    val autocorrect: Boolean = true,
)
