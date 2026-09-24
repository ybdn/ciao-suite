package dev.ybdn.ciao.clavier.ime

import android.inputmethodservice.InputMethodService
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
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.ybdn.ciao.clavier.domain.input.wordDeletionLength
import dev.ybdn.ciao.clavier.domain.layout.KeyboardMode
import dev.ybdn.ciao.designsystem.theme.CiaoTheme
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
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        keyboardView = null
        super.onDestroy()
    }

    override fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun deleteBackward() {
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
        val keyCode = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(abs(steps)) { sendDownUpKeyEvents(keyCode) }
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
        autoCapitalize = editorInfo != null &&
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
    }
}
