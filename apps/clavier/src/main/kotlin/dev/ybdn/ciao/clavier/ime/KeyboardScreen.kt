package dev.ybdn.ciao.clavier.ime

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ybdn.ciao.clavier.R
import dev.ybdn.ciao.clavier.domain.input.BackspaceRepeat
import dev.ybdn.ciao.clavier.domain.input.EffectiveShift
import dev.ybdn.ciao.clavier.domain.input.ShiftState
import dev.ybdn.ciao.clavier.domain.input.SpaceCursorDrag
import dev.ybdn.ciao.clavier.domain.input.afterCharacterTyped
import dev.ybdn.ciao.clavier.domain.input.effective
import dev.ybdn.ciao.clavier.domain.input.onShiftTap
import dev.ybdn.ciao.clavier.domain.layout.Key
import dev.ybdn.ciao.clavier.domain.layout.KeyVariants
import dev.ybdn.ciao.clavier.domain.layout.KeyboardLayout
import dev.ybdn.ciao.clavier.domain.layout.KeyboardMode
import dev.ybdn.ciao.clavier.domain.layout.KeyboardPage
import dev.ybdn.ciao.designsystem.components.BorderThin
import dev.ybdn.ciao.designsystem.components.BorderWidth
import dev.ybdn.ciao.designsystem.components.NeoKey
import dev.ybdn.ciao.designsystem.components.NeoTone
import dev.ybdn.ciao.designsystem.theme.DisplayFont
import dev.ybdn.ciao.designsystem.theme.NeoTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Délai maximal entre deux appuis sur ⇧ pour un verrouillage. */
private const val ShiftDoubleTapMs = 300L

/**
 * Glissement sur la barre d'espace : distance avant que le curseur ne bouge (en deçà, c'est un
 * espace), puis distance par caractère.
 */
private val SpaceDragThreshold = 12.dp
private val SpaceDragStep = 10.dp

/**
 * Dimensions calées sur Gboard (hauteur « normale », téléphone en portrait), **mesurées** sur une
 * capture de Gboard prise sur le Pixel 10 Pro (apps/clavier/docs/spec-v1.md §5.1) : touches de
 * 58 dp au pas de 70 dp, 6 dp entre deux touches, 4 dp de marge latérale, 8 dp au-dessus de la
 * première rangée. Une touche plus courte paraît écrasée : Gboard tient un rapport hauteur sur
 * largeur de 1,36, pas 1,1.
 */
private val KeyHeight = 58.dp
private val RowGap = 12.dp
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

/**
 * Hauteur réservée sous la dernière rangée pour les boutons qu'Android y dessine (masquer le
 * clavier, changer de clavier). Avec [BottomPadding], on retrouve les 76 dp que Gboard laisse
 * sous ses touches sur le même appareil.
 */
private val SystemKeyboardRowHeight = 68.dp

/** Taille des caractères (`charKey` de la maquette : `font-size: 21px`). */
private val CharacterKeyStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(fontSize = 21.sp)

/** Taille des étiquettes de fonction (« ?123 », « ABC » — `fnKey` de la maquette : `size: 15`). */
private val FunctionKeyStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp)

/** Taille du logo de la barre d'espace (`bottomRow` de la maquette : `size: 13`). */
private val SpaceLabelSize = 13.sp

/**
 * Clavier (apps/clavier/docs/spec-v1.md §6.1, §6.2) : page lettres AZERTY, deux pages de symboles
 * et pavés numérique, date et téléphone, selon [mode]. Page et majuscule repartent de zéro à chaque
 * nouveau champ ([inputSession]).
 */
@Composable
fun KeyboardScreen(
    mode: KeyboardMode,
    enterAction: EnterKeyAction,
    autoCapitalize: Boolean,
    inputSession: Int,
    actions: KeyboardActions,
) {
    var page by remember(inputSession) { mutableStateOf(KeyboardPage.Letters) }
    var shiftState by remember(inputSession) { mutableStateOf(ShiftState.Off) }
    // ⇧ maintenue : les lettres tapées pendant l'appui sont en majuscules, puis ⇧ retombe.
    var shiftHeld by remember { mutableStateOf(false) }
    val baseShift = shiftState.effective(autoCapitalize)
    val shift = if (shiftHeld && !baseShift.isUpper) EffectiveShift.Shift else baseShift

    val gestures = remember { GestureMemory() }
    val container = remember { KeyboardContainer() }
    val overlay = remember { KeyOverlayState() }
    val variantGeometry = rememberVariantGeometryFactory()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val palette = NeoTheme.palette
    val showPreview = mode == KeyboardMode.Text || mode == KeyboardMode.Email || mode == KeyboardMode.Url

    fun type(text: String) {
        actions.commitText(text)
        if (shiftHeld) {
            gestures.typedWhileShiftHeld = true
        } else {
            shiftState = shiftState.afterCharacterTyped()
        }
    }

    // Sous le clavier, Android dessine sa propre rangée (masquer le clavier, changer de clavier).
    // L'encart de barre de navigation ne la couvre pas toujours : on garde au moins sa hauteur.
    val navigationBarsInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val systemRowPadding = maxOf(navigationBarsInset, SystemKeyboardRowHeight)

    Box(modifier = Modifier.fillMaxWidth().onGloballyPositioned { container.coordinates = it }) {
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
                KeyboardLayout.rows(page, mode).forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(KeyHeight).padding(end = RowEndPadding),
                        horizontalArrangement = Arrangement.spacedBy(KeyGap),
                    ) {
                        row.forEachIndexed { columnIndex, slot ->
                            // Identité de la touche pour l'aperçu et les variantes : stable même si
                            // la touche se recompose pendant l'appui (majuscule qui change…).
                            val owner = KeyId(page, rowIndex, columnIndex)
                            val tone = if (slot.muted) NeoTone.Muted else NeoTone.Surface
                            when (val key = slot.key) {
                                is Key.Character, is Key.Text -> {
                                    val text = when (key) {
                                        is Key.Character -> (if (shift.isUpper) key.upper else key.lower).toString()
                                        is Key.Text -> key.text
                                        else -> error("unreachable")
                                    }
                                    val variants = KeyVariants.forCase(slot.variants, shift.isUpper)
                                    KeyboardKey(
                                        weight = slot.weight,
                                        tone = tone,
                                        container = container,
                                        touch = KeyTouch(
                                            onDown = { bounds, _ ->
                                                actions.keyFeedback()
                                                gestures.keyBounds = bounds
                                                if (showPreview) overlay.showPreview(owner, text, bounds)
                                            },
                                            onLongPress = if (variants.isEmpty()) null else {
                                                {
                                                    actions.keyFeedback()
                                                    overlay.showVariants(
                                                        owner,
                                                        variants,
                                                        variantGeometry(gestures.keyBounds, variants, container.width),
                                                    )
                                                }
                                            },
                                            onMove = { position -> overlay.selectVariantAt(owner, position) },
                                            onUp = {
                                                overlay.hidePreview(owner)
                                                if (overlay.hasVariants(owner)) {
                                                    overlay.closeVariants(owner)?.let(::type)
                                                } else {
                                                    type(text)
                                                }
                                            },
                                            onCancel = {
                                                overlay.hidePreview(owner)
                                                overlay.closeVariants(owner)
                                            },
                                            onAccessibilityClick = { type(text) },
                                        ),
                                    ) {
                                        Text(text, style = if (text.length > 1) FunctionKeyStyle else CharacterKeyStyle)
                                    }
                                }

                                is Key.Page -> KeyboardKey(
                                    weight = slot.weight,
                                    tone = tone,
                                    container = container,
                                    touch = KeyTouch(
                                        onDown = { _, _ -> actions.keyFeedback() },
                                        onUp = { page = key.target },
                                        onAccessibilityClick = { page = key.target },
                                    ),
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
                                    fun tapShift(isDoubleTap: Boolean) {
                                        shiftState = shiftState.onShiftTap(shiftState.effective(autoCapitalize), isDoubleTap)
                                    }

                                    // Relâchée après avoir tapé en la maintenant : la majuscule ne
                                    // valait que pendant l'appui.
                                    fun releaseShift() {
                                        shiftHeld = false
                                        if (gestures.typedWhileShiftHeld) shiftState = shiftState.afterCharacterTyped()
                                        gestures.typedWhileShiftHeld = false
                                    }
                                    KeyboardKey(
                                        weight = slot.weight,
                                        tone = if (shift.isUpper) NeoTone.Selected else tone,
                                        container = container,
                                        touch = KeyTouch(
                                            onDown = { _, _ ->
                                                actions.keyFeedback()
                                                // Le changement est immédiat, sans attendre de savoir
                                                // si un second appui suit : le second verrouille.
                                                val now = SystemClock.uptimeMillis()
                                                val isDoubleTap = now - gestures.lastShiftDownAt < ShiftDoubleTapMs
                                                gestures.lastShiftDownAt = if (isDoubleTap) 0L else now
                                                tapShift(isDoubleTap)
                                                shiftHeld = true
                                                gestures.typedWhileShiftHeld = false
                                            },
                                            onUp = ::releaseShift,
                                            onCancel = ::releaseShift,
                                            onAccessibilityClick = { tapShift(isDoubleTap = false) },
                                        ),
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

                                Key.Backspace -> {
                                    fun stopRepeat() {
                                        gestures.backspaceRepeat?.cancel()
                                        gestures.backspaceRepeat = null
                                    }
                                    KeyboardKey(
                                        weight = slot.weight,
                                        tone = tone,
                                        container = container,
                                        touch = KeyTouch(
                                            // Efface dès l'appui, puis se répète tant que le doigt
                                            // reste posé : caractère par caractère, puis mot par mot.
                                            onDown = { _, _ ->
                                                actions.keyFeedback()
                                                actions.deleteBackward()
                                                stopRepeat()
                                                gestures.backspaceRepeat = scope.launch {
                                                    delay(BackspaceRepeat.InitialDelayMs)
                                                    var repeat = 0
                                                    while (isActive) {
                                                        actions.keyFeedback()
                                                        if (BackspaceRepeat.deletesWord(repeat)) {
                                                            actions.deleteWordBackward()
                                                        } else {
                                                            actions.deleteBackward()
                                                        }
                                                        delay(BackspaceRepeat.delayAfter(repeat))
                                                        repeat++
                                                    }
                                                }
                                            },
                                            onUp = ::stopRepeat,
                                            onCancel = ::stopRepeat,
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

                                Key.Space -> KeyboardKey(
                                    weight = slot.weight,
                                    tone = tone,
                                    container = container,
                                    description = stringResource(R.string.key_space),
                                    touch = KeyTouch(
                                        onDown = { _, position ->
                                            actions.keyFeedback()
                                            gestures.spaceDownX = position.x
                                            gestures.spaceDrag = with(density) {
                                                SpaceCursorDrag(SpaceDragThreshold.toPx(), SpaceDragStep.toPx())
                                            }
                                        },
                                        onMove = { position ->
                                            val steps = gestures.spaceDrag?.onMove(position.x - gestures.spaceDownX) ?: 0
                                            if (steps != 0) actions.moveCursor(steps)
                                        },
                                        onUp = {
                                            if (gestures.spaceDrag?.isDragging != true) type(" ")
                                            gestures.spaceDrag = null
                                        },
                                        onCancel = { gestures.spaceDrag = null },
                                        onAccessibilityClick = { type(" ") },
                                    ),
                                ) {
                                    Text("C!ao", fontFamily = DisplayFont, fontSize = SpaceLabelSize)
                                }

                                Key.Emoji -> KeyboardKey(
                                    weight = slot.weight,
                                    tone = tone,
                                    container = container,
                                    // Le panneau emojis arrive au lot 6 : touche inerte, donc grisée
                                    // plutôt que faussement active.
                                    enabled = false,
                                    touch = KeyTouch(onAccessibilityClick = {}),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_key_emoji),
                                        contentDescription = stringResource(R.string.key_emoji),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }

                                Key.Enter -> KeyboardKey(
                                    weight = slot.weight,
                                    tone = NeoTone.Primary,
                                    container = container,
                                    touch = KeyTouch(
                                        onDown = { _, _ -> actions.keyFeedback() },
                                        onUp = actions::enter,
                                        onAccessibilityClick = actions::enter,
                                    ),
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

        KeyOverlay(overlay, containerWidth = { container.width })
    }
}

/** Position d'une touche dans la disposition affichée. */
private data class KeyId(val page: KeyboardPage, val row: Int, val column: Int)

/** Mémoire des gestes en cours, hors état Compose : rien à redessiner quand elle change. */
private class GestureMemory {
    var lastShiftDownAt = 0L
    var typedWhileShiftHeld = false
    var backspaceRepeat: Job? = null
    var keyBounds = Rect.Zero
    var spaceDrag: SpaceCursorDrag? = null
    var spaceDownX = 0f
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
