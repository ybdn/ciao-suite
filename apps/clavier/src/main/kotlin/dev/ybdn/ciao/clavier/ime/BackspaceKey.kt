package dev.ybdn.ciao.clavier.ime

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ybdn.ciao.clavier.R
import dev.ybdn.ciao.clavier.domain.input.BackspaceRepeat
import dev.ybdn.ciao.designsystem.components.NeoTone
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Répétition en cours du retour arrière : hors état Compose, rien à redessiner. */
private class RepeatJob {
    var job: Job? = null

    fun stop() {
        job?.cancel()
        job = null
    }
}

/**
 * Retour arrière (apps/clavier/docs/spec-v1.md §6.1) : efface dès l'appui, puis se répète tant que
 * le doigt reste posé, caractère par caractère, puis mot par mot. Partagé par les touches et le
 * panneau emojis.
 */
@Composable
internal fun RowScope.BackspaceKey(
    weight: Float,
    container: KeyboardContainer,
    actions: KeyboardActions,
    tone: NeoTone = NeoTone.Muted,
) {
    val scope = rememberCoroutineScope()
    val repeat = remember { RepeatJob() }
    KeyboardKey(
        weight = weight,
        tone = tone,
        container = container,
        touch = KeyTouch(
            onDown = { _, _ ->
                actions.keyFeedback()
                actions.deleteBackward()
                repeat.stop()
                repeat.job = scope.launch {
                    delay(BackspaceRepeat.InitialDelayMs)
                    var index = 0
                    while (isActive) {
                        actions.keyFeedback()
                        if (BackspaceRepeat.deletesWord(index)) actions.deleteWordBackward() else actions.deleteBackward()
                        delay(BackspaceRepeat.delayAfter(index))
                        index++
                    }
                }
            },
            onUp = repeat::stop,
            onCancel = repeat::stop,
            onAccessibilityClick = actions::deleteBackward,
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_key_backspace),
            contentDescription = stringResource(R.string.key_backspace),
            modifier = Modifier.size(width = 24.dp, height = 20.dp),
        )
    }
}
