package dev.ybdn.ciao.clavier.domain.input

/**
 * État mémorisé de la touche Majuscule (apps/clavier/docs/spec-v1.md §6.1). Il ne suffit pas à
 * savoir si la prochaine lettre sera une majuscule : la majuscule automatique (§6.3) dépend aussi
 * de la position du curseur dans le champ, d'où [effective].
 */
enum class ShiftState {
    /** Aucun choix de l'utilisateur : la majuscule automatique décide. */
    Off,

    /** Majuscule pour la prochaine lettre seulement. */
    Shift,

    /** Majuscules verrouillées jusqu'au prochain appui sur ⇧. */
    CapsLock,

    /** L'utilisateur a refusé la majuscule automatique proposée à cette position. */
    AutoDisabled,
}

/** État réellement appliqué, une fois la majuscule automatique prise en compte. */
enum class EffectiveShift {
    Off,

    /** Majuscule proposée par le champ (début de phrase), pas demandée par l'utilisateur. */
    Auto,
    Shift,
    CapsLock,
    ;

    val isUpper: Boolean get() = this != Off
}

/**
 * [autoCapitalize] vient du champ actif (`InputConnection.getCursorCapsMode`) : début de champ ou
 * de phrase, et seulement si le champ demande la majuscule automatique.
 */
fun ShiftState.effective(autoCapitalize: Boolean): EffectiveShift = when (this) {
    ShiftState.Shift -> EffectiveShift.Shift
    ShiftState.CapsLock -> EffectiveShift.CapsLock
    ShiftState.AutoDisabled -> EffectiveShift.Off
    ShiftState.Off -> if (autoCapitalize) EffectiveShift.Auto else EffectiveShift.Off
}

/**
 * Appui sur ⇧ : un appui simple bascule, un double appui verrouille, un appui alors que les
 * majuscules sont verrouillées déverrouille. Refuser une majuscule automatique ne vaut que pour
 * la position courante.
 */
fun ShiftState.onShiftTap(effective: EffectiveShift, isDoubleTap: Boolean): ShiftState = when {
    effective == EffectiveShift.CapsLock -> ShiftState.Off
    isDoubleTap -> ShiftState.CapsLock
    effective == EffectiveShift.Auto -> ShiftState.AutoDisabled
    effective == EffectiveShift.Shift -> ShiftState.Off
    else -> ShiftState.Shift
}

/**
 * Après un caractère : la majuscule ponctuelle et le refus de majuscule automatique retombent,
 * pour que la position suivante soit réévaluée ; le verrouillage reste.
 */
fun ShiftState.afterCharacterTyped(): ShiftState =
    if (this == ShiftState.CapsLock) ShiftState.CapsLock else ShiftState.Off
