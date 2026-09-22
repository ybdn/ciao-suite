package dev.ybdn.ciao.clavier.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
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
import dev.ybdn.ciao.designsystem.theme.CiaoTheme

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
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ClavierInputMethodService)
            setViewTreeViewModelStoreOwner(this@ClavierInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@ClavierInputMethodService)
            setContent {
                CiaoTheme {
                    KeyboardPlaceholder(
                        onInsertSpace = { currentInputConnection?.commitText(" ", 1) },
                    )
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        super.onDestroy()
    }
}
