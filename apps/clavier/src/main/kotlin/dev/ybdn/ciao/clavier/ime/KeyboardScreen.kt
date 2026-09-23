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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ybdn.ciao.clavier.R
import dev.ybdn.ciao.clavier.domain.input.EffectiveShift
import dev.ybdn.ciao.clavier.domain.input.ShiftState
import dev.ybdn.ciao.clavier.domain.input.afterCharacterTyped
import dev.ybdn.ciao.clavier.domain.input.effective
import dev.ybdn.ciao.clavier.domain.input.onShiftTap
import dev.ybdn.ciao.clavier.domain.layout.Key
import dev.ybdn.ciao.clavier.domain.layout.KeyboardLayout
import dev.ybdn.ciao.clavier.domain.layout.KeyboardPage
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

/**
 * Bandeau au-dessus des touches (suggestions, presse-papiers), comme la barre d'outils de Gboard :
 * `height: 48px; padding: 0px 8px; gap: 6px` dans la maquette, bouton presse-papiers de 36 × 34 px
 * suivi d'un séparateur vertical de 2 × 24 px.
 */
private val StripHeight = 48.dp
private val StripHorizontalPadding = 8.dp
private val StripGap = 6.dp
private val ClipboardButtonWidth = 36.dp
private val ClipboardButtonHeight = 34.dp
private val ClipboardIconSize = 18.dp
private val StripDividerHeight = 24.dp

/** Marge droite de chaque rangée, pour que l'ombre de la dernière touche ne soit pas coupée. */
private val RowEndPadding = 3.dp

/** Hauteur réservée à la rangée système sous le clavier (masquer le clavier, changer de clavier). */
private val SystemKeyboardRowHeight = 32.dp

/** Taille des caractères (`charKey` de la maquette : `font-size: 21px`). */
private val CharacterKeyStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(fontSize = 21.sp)

/** Taille des étiquettes de fonction (« ?123 », « ABC » — `fnKey` de la maquette : `size: 15`). */
private val FunctionKeyStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp)

/** Taille du logo de la barre d'espace (`bottomRow` de la maquette : `size: 13`). */
private val SpaceLabelSize = 13.sp

/**
 * Clavier : page lettres AZERTY et deux pages de symboles (apps/clavier/docs/spec-v1.md §6.1,
 * §6.2). Les accents par appui long, les claviers spécialisés, le curseur sur la barre d'espace
 * et l'aperçu de touche arrivent aux incréments suivants du lot 2.
 */
@Composable
fun KeyboardScreen(
    enterAction: EnterKeyAction,
    autoCapitalize: Boolean,
    onCommitText: (String) -> Unit,
    onDeleteBeforeCursor: () -> Unit,
    onEnter: () -> Unit,
    onKeyPress: () -> Unit,
) {
    var page by remember { mutableStateOf(KeyboardPage.Letters) }
    var shiftState by remember { mutableStateOf(ShiftState.Off) }
    val shift = shiftState.effective(autoCapitalize)
    val palette = NeoTheme.palette

    // Sous le clavier, Android dessine sa propre rangée (masquer le clavier, changer de clavier).
    // L'encart de barre de navigation ne la couvre pas toujours : on garde au moins sa hauteur.
    val navigationBarsInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val systemRowPadding = maxOf(navigationBarsInset, SystemKeyboardRowHeight)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.page)
            .padding(bottom = systemRowPadding),
    ) {
        // Bord supérieur du clavier : séparateur de section (anatomie §5.1 de la spec).
        HorizontalDivider(thickness = BorderWidth, color = palette.outline)

        SuggestionStrip()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SidePadding)
                .padding(top = TopPadding, bottom = BottomPadding),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            for (row in KeyboardLayout.rows(page)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(KeyHeight).padding(end = RowEndPadding),
                    horizontalArrangement = Arrangement.spacedBy(KeyGap),
                ) {
                    for (slot in row) {
                        val tone = if (slot.muted) NeoTone.Muted else NeoTone.Surface
                        when (val key = slot.key) {
                            is Key.Character -> {
                                val char = if (shift.isUpper) key.upper else key.lower
                                NeoKey(
                                    modifier = Modifier.weight(slot.weight).fillMaxHeight(),
                                    tone = tone,
                                    onClick = {
                                        onKeyPress()
                                        onCommitText(char.toString())
                                        shiftState = shiftState.afterCharacterTyped()
                                    },
                                ) {
                                    Text(char.toString(), style = CharacterKeyStyle)
                                }
                            }

                            is Key.Page -> NeoKey(
                                modifier = Modifier.weight(slot.weight).fillMaxHeight(),
                                tone = tone,
                                onClick = { onKeyPress(); page = key.target },
                            ) {
                                Text(key.label, style = FunctionKeyStyle)
                            }

                            Key.Shift -> {
                                val icon = when (shift) {
                                    EffectiveShift.Off -> R.drawable.ic_key_shift
                                    EffectiveShift.Auto, EffectiveShift.Shift -> R.drawable.ic_key_shift_on
                                    EffectiveShift.CapsLock -> R.drawable.ic_key_caps_lock
                                }
                                val description = when (shift) {
                                    EffectiveShift.Off -> R.string.key_shift
                                    EffectiveShift.Auto, EffectiveShift.Shift -> R.string.key_shift_on
                                    EffectiveShift.CapsLock -> R.string.key_caps_lock
                                }
                                NeoKey(
                                    modifier = Modifier.weight(slot.weight).fillMaxHeight(),
                                    tone = if (shift.isUpper) NeoTone.Selected else tone,
                                    onClick = {
                                        onKeyPress()
                                        shiftState = shiftState.onShiftTap(shift, isDoubleTap = false)
                                    },
                                    onDoubleClick = {
                                        onKeyPress()
                                        shiftState = shiftState.onShiftTap(shift, isDoubleTap = true)
                                    },
                                ) {
                                    Icon(
                                        painter = painterResource(icon),
                                        contentDescription = stringResource(description),
                                        modifier = if (shift == EffectiveShift.CapsLock) {
                                            Modifier.size(width = 20.dp, height = 22.dp)
                                        } else {
                                            Modifier.size(20.dp)
                                        },
                                    )
                                }
                            }

                            Key.Backspace -> BackspaceKey(
                                weight = slot.weight,
                                onDelete = onDeleteBeforeCursor,
                                onKeyPress = onKeyPress,
                            )

                            Key.Space -> NeoKey(
                                modifier = Modifier.weight(slot.weight).fillMaxHeight(),
                                tone = tone,
                                onClick = { onKeyPress(); onCommitText(" ") },
                            ) {
                                Text("C!ao", fontFamily = DisplayFont, fontSize = SpaceLabelSize)
                            }

                            Key.Emoji -> NeoKey(
                                modifier = Modifier.weight(slot.weight).fillMaxHeight(),
                                tone = tone,
                                // Le panneau emojis arrive au lot 6 : touche inerte, donc grisée
                                // plutôt que faussement active.
                                enabled = false,
                                onClick = {},
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_key_emoji),
                                    contentDescription = stringResource(R.string.key_emoji),
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            Key.Enter -> NeoKey(
                                modifier = Modifier.weight(slot.weight).fillMaxHeight(),
                                tone = NeoTone.Primary,
                                onClick = { onKeyPress(); onEnter() },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_key_enter),
                                    contentDescription = stringResource(enterAction.descriptionRes),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bandeau de 48 dp au-dessus des touches. Pour l'instant, seuls le bouton presse-papiers (inactif
 * jusqu'au lot 7) et le séparateur sont affichés ; les suggestions arrivent au lot 4. Le bandeau est
 * posé dès maintenant pour que le clavier ait la même hauteur que Gboard.
 */
@Composable
private fun SuggestionStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(StripHeight)
            .padding(horizontal = StripHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(StripGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeoKey(
            modifier = Modifier.width(ClipboardButtonWidth).height(ClipboardButtonHeight),
            tone = NeoTone.Muted,
            // Historique du presse-papiers : lot 7.
            enabled = false,
            onClick = {},
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_key_clipboard),
                contentDescription = stringResource(R.string.keyboard_clipboard_history),
                modifier = Modifier.size(ClipboardIconSize),
            )
        }
        Box(
            modifier = Modifier
                .width(BorderThin)
                .height(StripDividerHeight)
                .background(NeoTheme.palette.outline),
        )
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
                color = palette.surfaceMuted,
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
            Icon(
                painter = painterResource(R.drawable.ic_key_backspace),
                contentDescription = stringResource(R.string.key_backspace),
                modifier = Modifier.size(width = 24.dp, height = 20.dp),
            )
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
