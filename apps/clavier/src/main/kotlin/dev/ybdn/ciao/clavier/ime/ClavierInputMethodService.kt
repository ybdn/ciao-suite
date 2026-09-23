package dev.ybdn.ciao.clavier.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.ybdn.ciao.clavier.data.EmojiCatalog
import dev.ybdn.ciao.clavier.data.EmojiPreferences
import dev.ybdn.ciao.clavier.data.TypingPreferences
import dev.ybdn.ciao.clavier.data.clipboard.ClipboardHistory
import dev.ybdn.ciao.clavier.data.clipboard.ClipboardPreferences
import dev.ybdn.ciao.clavier.domain.clipboard.ClipboardRules
import dev.ybdn.ciao.clavier.domain.clipboard.ClipboardSettings
import dev.ybdn.ciao.clavier.domain.input.FrenchTypography
import dev.ybdn.ciao.clavier.domain.input.TextEdit
import dev.ybdn.ciao.clavier.domain.input.TypingSettings
import dev.ybdn.ciao.clavier.domain.input.wordDeletionLength
import dev.ybdn.ciao.clavier.domain.layout.KeyboardMode
import dev.ybdn.ciao.designsystem.theme.CiaoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Aucune permission réseau (garde-fou build-logic) : le service ne fait que composer l'interface
 * du clavier et parler à l'[android.view.inputmethod.InputConnection] du champ actif.
 *
 * `InputMethodService` n'est pas un `LifecycleOwner`/`ViewModelStoreOwner`/`SavedStateRegistryOwner`
 * par défaut (contrairement à `ComponentActivity`) : ils sont fournis ici à la main pour que la
 * `ComposeView` de l'interface du clavier fonctionne.
 */
class ClavierInputMethodService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    KeyboardActions {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var keyboardView: ComposeView? = null
    private var enterAction by mutableStateOf(EnterKeyAction.Newline)
    private var autoCapitalize by mutableStateOf(false)
    private var keyboardMode by mutableStateOf(KeyboardMode.Text)

    /** Incrémenté à chaque nouveau champ : le clavier repart de la page lettres, sans majuscule. */
    private var inputSession by mutableIntStateOf(0)

    /** Suivie par [onUpdateSelection] : le retour arrière efface alors la sélection entière. */
    private var hasSelection = false

    private var settings = TypingSettings()

    /**
     * Règles typographiques françaises (§6.3) applicables au champ actif : champ de texte
     * ordinaire seulement, jamais un mot de passe, une adresse e-mail ou une URL.
     */
    private var frenchRulesApply = false

    /**
     * Navigation privée ou champ mot de passe (§3) : le clavier n'y retient rien (emojis récents,
     * couleurs de peau, puis mots appris au lot 5).
     */
    private var incognito = false

    private val emojiPreferences by lazy { EmojiPreferences(this) }
    private var emojiData by mutableStateOf(EmojiPanelData())

    private val clipboardHistory by lazy { ClipboardHistory(this) }
    private val clipboardPreferences by lazy { ClipboardPreferences(this) }
    private var clipboardSettings = ClipboardSettings()
    private var clipboardData by mutableStateOf(ClipboardPanelData())

    /** Copie pour laquelle la puce « Coller » a déjà servi ou a été écartée. */
    private val dismissedChipId = MutableStateFlow<Long?>(null)

    /** Horloge pour l'expiration et la puce « Coller » : réévaluées chaque minute. */
    private val clock = MutableStateFlow(System.currentTimeMillis())

    private val clipboardManager by lazy { getSystemService(ClipboardManager::class.java) }
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener(::onClipChanged)

    /** Instant de la dernière espace tapée seule, pour le double espace → point ; 0 sinon. */
    private var lastSpaceAt = 0L

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        // Le recomposeur de Compose cherche le ViewTreeLifecycleOwner en remontant depuis la
        // racine de la fenêtre de l'IME (un conteneur système au-dessus de la vue qu'on renvoie
        // depuis onCreateInputView), pas seulement sur la ComposeView elle-même : sans ceci,
        // IllegalStateException "ViewTreeLifecycleOwner not found" au premier affichage.
        window.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        lifecycleScope.launch {
            TypingPreferences(this@ClavierInputMethodService).settings.collect {
                settings = it
                refreshAutoCapitalize()
            }
        }
        lifecycleScope.launch {
            val categories = EmojiCatalog.load(this@ClavierInputMethodService)
            combine(emojiPreferences.recents, emojiPreferences.skinTones) { recents, skinTones ->
                EmojiPanelData(categories, recents, skinTones)
            }.collect { emojiData = it }
        }
        lifecycleScope.launch {
            combine(clipboardHistory.items, clipboardPreferences.settings, dismissedChipId, clock) { items, settings, dismissed, now ->
                clipboardSettings = settings
                ClipboardPanelData(
                    items = if (settings.historyEnabled) ClipboardRules.displayed(items, now, settings.retention) else emptyList(),
                    historyEnabled = settings.historyEnabled,
                    pasteChip = ClipboardRules.pasteChip(items, now)?.takeIf { settings.historyEnabled && it.id != dismissed },
                )
            }.collect { clipboardData = it }
        }
        lifecycleScope.launch {
            while (true) {
                clock.value = System.currentTimeMillis()
                clipboardHistory.prune(clock.value, clipboardSettings.retention)
                delay(ClockTickMs)
            }
        }
        // En tant que clavier actif, le service peut lire le presse-papiers en arrière-plan.
        clipboardManager?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        return ComposeView(this).apply {
            setContent {
                CiaoTheme {
                    KeyboardScreen(
                        mode = keyboardMode,
                        enterAction = enterAction,
                        autoCapitalize = autoCapitalize,
                        inputSession = inputSession,
                        emojiData = emojiData,
                        clipboardData = clipboardData,
                        actions = this@ClavierInputMethodService,
                    )
                }
            }
        }.also { keyboardView = it }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        enterAction = info.enterKeyAction()
        keyboardMode = keyboardModeFor(info?.inputType ?: 0)
        frenchRulesApply = keyboardMode == KeyboardMode.Text && !isPasswordField(info?.inputType ?: 0)
        lastSpaceAt = 0L
        incognito = info != null && (
            info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0 ||
                isPasswordField(info.inputType)
            )
        if (!restarting) inputSession++
        hasSelection = info != null && info.initialSelStart != info.initialSelEnd
        refreshAutoCapitalize()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd,
        )
        hasSelection = newSelStart != newSelEnd
        // Le champ vient d'être modifié (par le clavier ou par l'app) : la position du curseur
        // décide de la majuscule automatique.
        refreshAutoCapitalize()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        keyboardView = null
        super.onDestroy()
    }

    override fun commitText(text: String) {
        val connection = currentInputConnection ?: return
        val edit = typographyEdit(text)
        if (edit == null) {
            connection.commitText(text, 1)
        } else {
            connection.beginBatchEdit()
            connection.deleteSurroundingText(edit.deleteBefore, 0)
            connection.commitText(edit.insert, 1)
            connection.endBatchEdit()
        }
        // Une espace transformée en point ne compte pas : une troisième espace reste une espace.
        lastSpaceAt = if (text == " " && edit == null) SystemClock.uptimeMillis() else 0L
    }

    /** Règles typographiques françaises (§6.3) : modification à faire au lieu d'insérer [text]. */
    private fun typographyEdit(text: String): TextEdit? {
        if (!frenchRulesApply || hasSelection || text.length != 1) return null
        val connection = currentInputConnection ?: return null
        val char = text[0]
        return when {
            char == ' ' && settings.doubleSpacePeriod &&
                SystemClock.uptimeMillis() - lastSpaceAt < DoubleSpaceMaxDelayMs ->
                connection.getTextBeforeCursor(TypographyLookBehind, 0)
                    ?.let(FrenchTypography::doubleSpacePeriod)

            settings.nonBreakingSpace && char in ";:!?" ->
                connection.getTextBeforeCursor(TypographyLookBehind, 0)
                    ?.let { FrenchTypography.spaceBeforePunctuation(char, it) }

            else -> null
        }
    }

    override fun deleteBackward() {
        lastSpaceAt = 0L
        val connection = currentInputConnection ?: return
        when {
            hasSelection -> connection.commitText("", 1)
            // Champ vide : certaines apps attendent la touche elle-même (supprimer un destinataire…).
            connection.getTextBeforeCursor(1, 0).isNullOrEmpty() -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            // En points de code, pour ne pas couper un emoji en deux (paire de substitution).
            else -> connection.deleteSurroundingTextInCodePoints(1, 0)
        }
    }

    override fun deleteWordBackward() {
        val connection = currentInputConnection ?: return
        if (hasSelection) {
            connection.commitText("", 1)
            return
        }
        val before = connection.getTextBeforeCursor(WordLookBehind, 0) ?: return
        val length = wordDeletionLength(before)
        if (length > 0) connection.deleteSurroundingText(length, 0)
    }

    override fun moveCursor(steps: Int) {
        lastSpaceAt = 0L
        val keyCode = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(abs(steps)) { sendDownUpKeyEvents(keyCode) }
    }

    override fun emojiTyped(emoji: String) {
        lastSpaceAt = 0L
        currentInputConnection?.commitText(emoji, 1) ?: return
        if (!incognito) lifecycleScope.launch { emojiPreferences.addRecent(emoji) }
    }

    override fun skinToneChosen(base: String, variant: String) {
        if (!incognito) lifecycleScope.launch { emojiPreferences.setSkinTone(base, variant) }
    }

    /**
     * Nouvelle copie (spec §9) : texte seulement, jamais une copie marquée sensible
     * (`EXTRA_IS_SENSITIVE`, posée par les gestionnaires de mots de passe).
     */
    private fun onClipChanged() {
        if (!clipboardSettings.historyEnabled) return
        val clip = clipboardManager?.primaryClip ?: return
        val description = clip.description
        val isSensitive = description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
        val isText = description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
            description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        val text = clip.takeIf { it.itemCount > 0 && isText }?.getItemAt(0)?.text
        if (!ClipboardRules.accepts(text, isSensitive)) return
        val now = System.currentTimeMillis()
        clock.value = now
        lifecycleScope.launch {
            clipboardHistory.add(text.toString(), now, clipboardSettings.retention)
            // La puce « Coller » disparaît une minute après la copie.
            delay(ClipboardRules.PasteChipMillis + 1)
            clock.value = System.currentTimeMillis()
        }
    }

    override fun pasteClip(text: String) {
        lastSpaceAt = 0L
        currentInputConnection?.commitText(text, 1)
        dismissPasteChip()
    }

    override fun setClipPinned(id: Long, pinned: Boolean) {
        lifecycleScope.launch { clipboardHistory.setPinned(id, pinned) }
    }

    override fun deleteClip(id: Long) {
        lifecycleScope.launch { clipboardHistory.delete(id) }
    }

    override fun dismissPasteChip() {
        dismissedChipId.value = clipboardData.pasteChip?.id
    }

    override fun keyFeedback() {
        keyboardView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /**
     * Majuscule automatique (apps/clavier/docs/spec-v1.md §6.3) : c'est le champ qui décide, via
     * `getCursorCapsMode`, filtré par les drapeaux qu'il demande (`TYPE_TEXT_FLAG_CAP_*`). Un champ
     * qui ne demande rien — mot de passe, URL — n'aura jamais de majuscule automatique.
     */
    private fun refreshAutoCapitalize() {
        val editorInfo = currentInputEditorInfo
        val connection = currentInputConnection
        autoCapitalize = settings.autoCapitalize &&
            editorInfo != null &&
            connection != null &&
            connection.getCursorCapsMode(editorInfo.inputType) != 0
    }

    /** Envoie l'action du champ (Envoyer, Rechercher…) si `imeOptions` en demande une, sinon un retour à la ligne. */
    override fun enter() {
        val editorInfo = currentInputEditorInfo
        val action = (editorInfo?.imeOptions ?: EditorInfo.IME_ACTION_UNSPECIFIED) and EditorInfo.IME_MASK_ACTION
        val noEnterFlag = (editorInfo?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION
        val hasAction = editorInfo != null &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED &&
            action != EditorInfo.IME_ACTION_NONE &&
            noEnterFlag == 0
        if (hasAction) {
            currentInputConnection?.performEditorAction(action)
        } else {
            currentInputConnection?.commitText("\n", 1)
        }
    }

    private companion object {
        /** Assez pour contenir le plus long mot français et les espaces qui le suivent. */
        const val WordLookBehind = 64

        /** Assez pour les règles typographiques : un mot court (« https ») et son espace. */
        const val TypographyLookBehind = 8

        /** Réévaluation de l'expiration de l'historique et de la puce « Coller ». */
        const val ClockTickMs = 60_000L

        /** Au-delà, deux espaces tapées l'une après l'autre restent deux espaces. */
        const val DoubleSpaceMaxDelayMs = 1_000L
    }
}
