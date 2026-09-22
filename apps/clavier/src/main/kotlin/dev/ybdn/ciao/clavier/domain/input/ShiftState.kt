package dev.ybdn.ciao.clavier.domain.input

/**
 * État de la touche Majuscule (apps/clavier/docs/spec-v1.md §6.1) : un appui = majuscule pour la
 * lettre suivante ; un second appui rapide (double appui) = verrouillage ; un appui sur ⇧ alors
 * qu'elle est verrouillée déverrouille.
 */
enum class ShiftState {
    Off, Shift, CapsLock;

    val isUpper: Boolean get() = this != Off
}

/** Appui simple ou double sur ⇧ (Kotlin pur, testable indépendamment du geste réel). */
fun ShiftState.onShiftTap(isDoubleTap: Boolean): ShiftState = when {
    this == ShiftState.CapsLock -> ShiftState.Off
    isDoubleTap -> ShiftState.CapsLock
    this == ShiftState.Off -> ShiftState.Shift
    else -> ShiftState.Off
}

/** Une majuscule ponctuelle ([ShiftState.Shift]) retombe après la lettre suivante ; le verrouillage reste. */
fun ShiftState.afterLetterTyped(): ShiftState = if (this == ShiftState.Shift) ShiftState.Off else this
