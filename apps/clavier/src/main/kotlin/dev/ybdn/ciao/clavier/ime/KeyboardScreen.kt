package dev.ybdn.ciao.clavier.ime

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.ybdn.ciao.clavier.domain.input.ShiftState
import dev.ybdn.ciao.clavier.domain.input.afterLetterTyped
import dev.ybdn.ciao.clavier.domain.input.onShiftTap
import dev.ybdn.ciao.clavier.domain.layout.AzertyLayout
import dev.ybdn.ciao.clavier.domain.layout.Key
import dev.ybdn.ciao.designsystem.components.BorderThin
import dev.ybdn.ciao.designsystem.components.NeoKey
import dev.ybdn.ciao.designsystem.components.NeoTone
import dev.ybdn.ciao.designsystem.components.PressOffset
import dev.ybdn.ciao.designsystem.components.PressReleaseMillis
import dev.ybdn.ciao.designsystem.components.ShadowSmall
import dev.ybdn.ciao.designsystem.components.neoSurface
import dev.ybdn.ciao.designsystem.theme.NeoTheme
import kotlinx.coroutines.delay

/** Avant la première répétition du retour arrière, et cadence des répétitions suivantes. */
private const val BackspaceInitialDelayMs = 400L
private const val BackspaceRepeatDelayMs = 60L

/**
 * Page lettres du clavier AZERTY (apps/clavier/docs/spec-v1.md §6.1). Pages symboles et claviers
 * spécialisés arrivent aux incréments suivants du lot 2.
 */
@Composable
fun KeyboardScreen(
    enterLabel: EnterKeyLabel,
    onCommitText: (String) -> Unit,
    onDeleteBeforeCursor: () -> Unit,
    onEnter: () -> Unit,
    onKeyPress: () -> Unit,
) {
    var shiftState by remember { mutableStateOf(ShiftState.Off) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in AzertyLayout.rows) {
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (slot in row) {
                    when (val key = slot.key) {
                        is Key.Letter -> LetterKey(
                            weight = slot.weight,
                            char = if (shiftState.isUpper) key.upper else key.lower,
                            onTap = {
                                onKeyPress()
                                onCommitText((if (shiftState.isUpper) key.upper else key.lower).toString())
                                shiftState = shiftState.afterLetterTyped()
                            },
                        )

                        Key.Apostrophe -> LetterKey(
                            weight = slot.weight,
                            char = '\'',
                            onTap = { onKeyPress(); onCommitText("'") },
                        )

                        Key.Comma -> LetterKey(
                            weight = slot.weight,
                            char = ',',
                            onTap = { onKeyPress(); onCommitText(",") },
                        )

                        Key.Period -> LetterKey(
                            weight = slot.weight,
                            char = '.',
                            onTap = { onKeyPress(); onCommitText(".") },
                        )

                        Key.Shift -> NeoKey(
                            modifier = Modifier.weight(slot.weight).fillMaxHeight(),
                            tone = if (shiftState.isUpper) NeoTone.Selected else NeoTone.Muted,
                            onClick = {
                                onKeyPress()
                                shiftState = shiftState.onShiftTap(isDoubleTap = false)
                            },
                            onDoubleClick = {
                                onKeyPress()
                                shiftState = shiftState.onShiftTap(isDoubleTap = true)
                            },
                        ) {
                            Text(
                                if (shiftState == ShiftState.CapsLock) "⇪" else "⇧",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }

                        Key.Backspace -> BackspaceKey(
                            weight = slot.weight,
                            onDelete = onDeleteBeforeCursor,
                            onKeyPress = onKeyPress,
                        )

                        Key.Space -> NeoKey(
                            modifier = Modifier.weight(slot.weight).fillMaxHeight(),
                            onClick = { onKeyPress(); onCommitText(" ") },
                        ) {}

                        Key.Symbols -> NeoKey(
                            modifier = Modifier.weight(slot.weight).fillMaxHeight(),
                            tone = NeoTone.Muted,
                            // Page symboles : arrive au prochain incrément du lot 2.
                            enabled = false,
                            onClick = {},
                        ) { Text("?123", style = MaterialTheme.typography.bodySmall) }

                        Key.Emoji -> NeoKey(
                            modifier = Modifier.weight(slot.weight).fillMaxHeight(),
                            tone = NeoTone.Muted,
                            // Panneau emojis : lot 6.
                            enabled = false,
                            onClick = {},
                        ) { Text("😊", style = MaterialTheme.typography.titleMedium) }

                        Key.Enter -> NeoKey(
                            modifier = Modifier.weight(slot.weight).fillMaxHeight(),
                            tone = NeoTone.Primary,
                            onClick = { onKeyPress(); onEnter() },
                        ) { Text(enterLabel.symbol, style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.LetterKey(weight: Float, char: Char, onTap: () -> Unit) {
    NeoKey(
        modifier = Modifier.weight(weight).fillMaxHeight(),
        onClick = onTap,
    ) {
        Text(char.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Retour arrière : un appui simple efface un caractère ; un appui maintenu répète après un
 * court délai. L'accélération (lettre puis mot) décrite en §6.1 de la spec est laissée à un
 * prochain incrément. Implémentée à la main (pas via [NeoKey]) : elle doit agir dès l'appui,
 * puis se répéter tant que le doigt reste posé, ce que `combinedClickable` ne permet pas.
 */
@Composable
private fun RowScope.BackspaceKey(weight: Float, onDelete: () -> Unit, onKeyPress: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressOffset by animateDpAsState(
        targetValue = if (isPressed) PressOffset else 0.dp,
        animationSpec = tween(durationMillis = if (isPressed) 0 else PressReleaseMillis),
        label = "backspacePressOffset",
    )
    val palette = NeoTheme.palette

    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .offset(pressOffset, pressOffset)
            .neoSurface(
                color = palette.surface,
                outline = palette.outline,
                shadowOffset = if (pressOffset == 0.dp) ShadowSmall else 0.dp,
                borderWidth = BorderThin,
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val press = PressInteraction.Press(it)
                        interactionSource.emit(press)
                        onKeyPress()
                        onDelete()
                        val released = tryAwaitRelease()
                        interactionSource.emit(
                            if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press),
                        )
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("⌫", style = MaterialTheme.typography.titleMedium)
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(BackspaceInitialDelayMs)
            while (isPressed) {
                onKeyPress()
                onDelete()
                delay(BackspaceRepeatDelayMs)
            }
        }
    }
}
