package dev.ybdn.ciao.clavier.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoTone
import kotlinx.coroutines.delay

/**
 * Effacement irréversible en deux appuis : le premier change le libellé en [confirmText], le
 * second efface. Sans second appui, le bouton revient à [text] au bout de quelques secondes.
 */
@Composable
fun ConfirmingDangerButton(text: String, confirmText: String, enabled: Boolean, onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(ConfirmWindowMs)
            armed = false
        }
    }
    NeoButton(
        text = if (armed) confirmText else text,
        tone = NeoTone.Danger,
        enabled = enabled,
        onClick = {
            if (armed) onConfirm()
            armed = !armed
        },
    )
}

private const val ConfirmWindowMs = 4_000L
