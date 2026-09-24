package dev.ybdn.ciao.clavier.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.ybdn.ciao.clavier.R
import dev.ybdn.ciao.clavier.ime.ClavierInputMethodService
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoCard
import dev.ybdn.ciao.designsystem.components.NeoNotice
import dev.ybdn.ciao.designsystem.components.NeoScreen
import dev.ybdn.ciao.designsystem.components.NeoSectionHeader
import dev.ybdn.ciao.designsystem.components.NeoTag
import dev.ybdn.ciao.designsystem.components.NeoTextField
import dev.ybdn.ciao.designsystem.components.NeoTone

/**
 * Identifiant d'un IME au format court qu'utilisent [InputMethodManager]/`Settings.Secure`
 * (`package/.RelativeClassName`), pas le nom pleinement qualifié de la classe.
 */
private fun imeId(context: Context): String =
    ComponentName(context, ClavierInputMethodService::class.java).flattenToShortString()

private fun isEnabled(context: Context): Boolean {
    val manager = context.getSystemService(InputMethodManager::class.java) ?: return false
    val id = imeId(context)
    return manager.enabledInputMethodList.any { it.id == id }
}

private fun isSelected(context: Context): Boolean {
    val selected = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    return selected == imeId(context)
}

/**
 * Accueil / mise en route (apps/clavier/docs/spec-v1.md §10.1) : tant que le clavier n'est pas
 * activé puis choisi, un parcours en deux étapes, suivi d’une zone de test et des réglages de frappe.
 */
@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(isEnabled(context)) }
    var selected by remember { mutableStateOf(isSelected(context)) }

    // L'utilisateur active/choisit le clavier dans les réglages système, puis revient sur cet
    // écran : on relit l'état à chaque retour au premier plan.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = isEnabled(context)
                selected = isSelected(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var testText by remember { mutableStateOf("") }

    NeoScreen(title = stringResource(R.string.onboarding_title)) {
        NeoNotice(stringResource(R.string.onboarding_intro), tone = NeoTone.Info)

        NeoCard {
            NeoSectionHeader("01", stringResource(R.string.onboarding_step_enable_title))
            if (enabled) {
                NeoTag(stringResource(R.string.onboarding_step_enable_done), tone = NeoTone.Success)
            } else {
                NeoNotice(stringResource(R.string.onboarding_step_enable_body), tone = NeoTone.Warning)
                NeoButton(
                    text = stringResource(R.string.onboarding_step_enable_action),
                    onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                )
            }
        }

        NeoCard {
            NeoSectionHeader("02", stringResource(R.string.onboarding_step_select_title))
            if (selected) {
                NeoTag(stringResource(R.string.onboarding_step_select_done), tone = NeoTone.Success)
            } else {
                NeoNotice(stringResource(R.string.onboarding_step_select_body), tone = NeoTone.Warning)
                NeoButton(
                    text = stringResource(R.string.onboarding_step_select_action),
                    enabled = enabled,
                    onClick = {
                        context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
                    },
                )
            }
        }

        NeoCard {
            NeoSectionHeader("03", stringResource(R.string.onboarding_test_title))
            NeoTextField(
                label = stringResource(R.string.onboarding_test_label),
                value = testText,
                onValueChange = { testText = it },
                placeholder = stringResource(R.string.onboarding_test_placeholder),
                // Champ de rédaction : il demande la majuscule automatique, que le clavier lit
                // via getCursorCapsMode.
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }

        TypingPreferencesCard(index = "04")

        CorrectionPreferencesCard(index = "05")

        ClipboardPreferencesCard(index = "06")

        DataCard(index = "07")
    }
}
