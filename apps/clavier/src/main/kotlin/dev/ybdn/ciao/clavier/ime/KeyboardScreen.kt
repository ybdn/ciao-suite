package dev.ybdn.ciao.clavier.ime

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ybdn.ciao.clavier.domain.input.ShiftState
import dev.ybdn.ciao.clavier.domain.input.afterLetterTyped
import dev.ybdn.ciao.clavier.domain.input.onShiftTap
import dev.ybdn.ciao.clavier.domain.layout.AzertyLayout
import dev.ybdn.ciao.clavier.domain.layout.Key
import dev.ybdn.ciao.designsystem.components.BorderThin
import dev.ybdn.ciao.designsystem.components.BorderWidth
import dev.ybdn.ciao.designsystem.components.NeoKey
import dev.ybdn.ciao.designsystem.components.NeoTone
import dev.ybdn.ciao.designsystem.components.PressOffset
import dev.ybdn.ciao.designsystem.components.PressReleaseMillis
import dev.ybdn.ciao.designsystem.components.ShadowSmall
import dev.ybdn.ciao.designsystem.components.neoSurface
import dev.ybdn.ciao.designsystem.theme.DisplayFont
import dev.ybdn.ciao.designsystem.theme.NeoTheme
import kotlinx.coroutines.delay

/** Avant la première répétition du retour arrière, et cadence des répétitions suivantes. */
private const val BackspaceInitialDelayMs = 400L
private const val BackspaceRepeatDelayMs = 60L

/**
 * Dimensions calées sur Gboard (hauteur « normale », téléphone en portrait), reprises au pixel
 * près de la maquette (apps/clavier/docs/spec-v1.md §5.1) : touches de 46 dp au pas de 56 dp,
 * 6 dp entre deux touches, 4 dp de marge latérale, 8 dp en haut et en bas.
 */
private val KeyHeight = 46.dp
private val RowGap = 10.dp
private val KeyGap = 6.dp
private val SidePadding = 4.dp
private val TopPadding = 8.dp
private val BottomPadding = 8.dp

/** Marge droite de chaque rangée, pour que l'ombre de la dernière touche ne soit pas coupée. */
private val RowEndPadding = 3.dp

/** Taille des lettres (`charKey` de la maquette : `font-size: 21px`). */
private val LetterKeyStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(fontSize = 21.sp)

/** Taille des icônes/étiquettes de fonction (⇧ ⌫ ⏎ 😊 ?123 — `fnKey` de la maquette : `size: 15`). */
private val FunctionKeyStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp)

/** Taille du logo de la barre d'espace (`bottomRow` de la maquette : `size: 13`). */
private val SpaceLabelSize = 13.sp

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
    val palette = NeoTheme.palette

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.page)
            .navigationBarsPadding(),
    ) {
        // Bord supérieur du clavier : séparateur de section (anatomie §5.1 de la spec).
        HorizontalDivider(thickness = BorderWidth, color = palette.outline)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SidePadding)
                .padding(top = TopPadding, bottom = BottomPadding),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            for (row in AzertyLayout.rows) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(KeyHeight).padding(end = RowEndPadding),
                    horizontalArrangement = Arrangement.spacedBy(KeyGap),
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
                                    style = FunctionKeyStyle,
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
                            ) {
                                Text("C!ao", fontFamily = DisplayFont, fontSize = SpaceLabelSize)
                            }

                            Key.Symbols -> NeoKey(
                                modifier = Modifier.weight(slot.weight).fillMaxHeight(),
                                tone = NeoTone.Muted,
                                // Page symboles : arrive au prochain incrément du lot 2.
                                enabled = false,
                                onClick = {},
                            ) { Text("?123", style = FunctionKeyStyle) }

                            Key.Emoji -> NeoKey(
                                modifier = Modifier.weight(slot.weight).fillMaxHeight(),
                                tone = NeoTone.Muted,
                                // Panneau emojis : lot 6.
                                enabled = false,
                                onClick = {},
                            ) { Text("😊", style = FunctionKeyStyle) }

                            Key.Enter -> NeoKey(
                                modifier = Modifier.weight(slot.weight).fillMaxHeight(),
                                tone = NeoTone.Primary,
                                onClick = { onKeyPress(); onEnter() },
                            ) { Text(enterLabel.symbol, style = FunctionKeyStyle) }
                        }
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
        Text(char.toString(), style = LetterKeyStyle)
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
        CompositionLocalProvider(LocalContentColor provides palette.content) {
            Text("⌫", style = FunctionKeyStyle)
        }
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
