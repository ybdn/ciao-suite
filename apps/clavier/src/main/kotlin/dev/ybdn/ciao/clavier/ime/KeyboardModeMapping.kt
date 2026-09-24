package dev.ybdn.ciao.clavier.ime

import android.text.InputType
import dev.ybdn.ciao.clavier.domain.layout.KeyboardMode

/** Clavier adapté au type du champ actif (apps/clavier/docs/spec-v1.md §6.2). */
fun keyboardModeFor(inputType: Int): KeyboardMode {
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER -> KeyboardMode.Number
        InputType.TYPE_CLASS_DATETIME -> KeyboardMode.DateTime
        InputType.TYPE_CLASS_PHONE -> KeyboardMode.Phone
        InputType.TYPE_CLASS_TEXT -> when (variation) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            -> KeyboardMode.Email
            InputType.TYPE_TEXT_VARIATION_URI -> KeyboardMode.Url
            else -> KeyboardMode.Text
        }
        else -> KeyboardMode.Text
    }
}

/** Champ mot de passe (apps/clavier/docs/spec-v1.md §3) : aucune règle ni suggestion n'y touche. */
fun isPasswordField(inputType: Int): Boolean {
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> false
    }
}

/**
 * Suggestions permises dans le champ (apps/clavier/docs/spec-v1.md §7.1) : texte ordinaire
 * seulement, jamais un mot de passe, une adresse e-mail ou web, ni un champ qui les refuse
 * (`TYPE_TEXT_FLAG_NO_SUGGESTIONS`). La navigation privée est vérifiée à part.
 */
fun suggestionsAllowed(inputType: Int): Boolean =
    inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
        keyboardModeFor(inputType) == KeyboardMode.Text &&
        !isPasswordField(inputType) &&
        inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0

/**
 * Autocorrection permise : en plus des suggestions, le champ la demande
 * (`TYPE_TEXT_FLAG_AUTO_CORRECT`) ou est un champ de rédaction sur plusieurs lignes. Même règle
 * que le clavier d'AOSP : un champ d'une ligne sans ce drapeau (identifiant, nom…) n'est pas corrigé.
 */
fun autocorrectAllowed(inputType: Int): Boolean =
    suggestionsAllowed(inputType) &&
        inputType and (InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
