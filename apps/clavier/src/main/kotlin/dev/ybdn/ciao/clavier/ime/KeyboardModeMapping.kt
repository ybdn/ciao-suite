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
