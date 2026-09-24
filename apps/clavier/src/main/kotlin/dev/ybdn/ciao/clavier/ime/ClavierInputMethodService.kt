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
import dev.ybdn.ciao.clavier.data.FrenchDictionary
import dev.ybdn.ciao.clavier.data.TypingPreferences
import dev.ybdn.ciao.clavier.data.clipboard.ClipboardHistory
import dev.ybdn.ciao.clavier.data.clipboard.ClipboardPreferences
import dev.ybdn.ciao.clavier.data.personal.PersonalDictionary
import dev.ybdn.ciao.clavier.domain.clipboard.ClipboardRules
import dev.ybdn.ciao.clavier.domain.clipboard.ClipboardSettings
import dev.ybdn.ciao.clavier.domain.input.FrenchTypography
import dev.ybdn.ciao.clavier.domain.input.TextEdit
import dev.ybdn.ciao.clavier.domain.input.TypingSettings
import dev.ybdn.ciao.clavier.domain.input.wordDeletionLength
import dev.ybdn.ciao.clavier.domain.layout.KeyboardMode
import dev.ybdn.ciao.clavier.domain.suggest.PersonalLexicon
import dev.ybdn.ciao.clavier.domain.suggest.SuggestionEngine
import dev.ybdn.ciao.clavier.domain.suggest.WordAtCursor
import dev.ybdn.ciao.designsystem.theme.CiaoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
     * couleurs de peau, mots appris).
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

    /**
     * Moteur de suggestions, une fois le dictionnaire chargé (en arrière-plan, au démarrage),
     * refait avec les mots appris à chaque changement du dictionnaire personnel.
     */
    private var engine: SuggestionEngine? = null
    private val personalDictionary by lazy { PersonalDictionary(this) }
    private var suggestionBar by mutableStateOf(SuggestionBar())

    /** Calcul des suggestions hors du fil principal ; celui d'une frappe précédente est annulé (§4). */
    private val suggestDispatcher = Dispatchers.Default.limitedParallelism(1)
    private var suggestJob: Job? = null

    /** Dernier calcul affiché : l'autocorrection le réutilise s'il porte sur le même mot. */
    private var lastComputed: ComputedSuggestions? = null

    /**
     * Suggestions et autocorrection permises par le champ actif (§3, §7.1), avant les réglages :
     * jamais en navigation privée ni dans un mot de passe.
     */
    private var suggestionsApply = false
    private var autocorrectApply = false

    /** Dernière autocorrection, qu'un retour arrière immédiat annule (§7.1). */
    private var lastAutocorrection: AutocorrectionUndo? = null

    /** Mots dont l'autocorrection a été annulée : plus corrigés jusqu'au prochain champ. */
    private val rejectedWords = HashSet<String>()

    /** Espace ajoutée après une suggestion choisie : une ponctuation tapée ensuite la remplace. */
    private var autoSpacePending = false

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
                refreshSuggestions()
            }
        }
        lifecycleScope.launch {
            val dictionary = FrenchDictionary.load(this@ClavierInputMethodService)
            val base = SuggestionEngine(dictionary)
            engine = base
            refreshSuggestions()
            personalDictionary.words.collect { words ->
                engine = withContext(suggestDispatcher) { base.withPersonal(PersonalLexicon.build(dictionary, words)) }
                refreshSuggestions()
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
                        suggestions = { suggestionBar },
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
        suggestionsApply = info != null && !incognito && suggestionsAllowed(info.inputType)
        autocorrectApply = suggestionsApply && autocorrectAllowed(info?.inputType ?: 0)
        lastAutocorrection = null
        autoSpacePending = false
        if (!restarting) {
            inputSession++
            rejectedWords.clear()
        }
        hasSelection = info != null && info.initialSelStart != info.initialSelEnd
        refreshAutoCapitalize()
        refreshSuggestions()
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
        // décide de la majuscule automatique et du mot à compléter.
        refreshAutoCapitalize()
        refreshSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        suggestJob?.cancel()
        suggestionBar = SuggestionBar()
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
        lastAutocorrection = null
        val afterAutoSpace = autoSpacePending
        autoSpacePending = false
        // « bonjour » choisi puis « . » : le point prend la place de l'espace ajoutée.
        if (afterAutoSpace && replacesAutoSpace(text) && connection.getTextBeforeCursor(1, 0)?.toString() == " ") {
            connection.deleteSurroundingText(1, 0)
        }
        if (isWordSeparator(text) && !hasSelection) {
            if (autocorrectEnabled() && autocorrectBefore(text)) return
            // Après une suggestion choisie, le mot a déjà compté (pickSuggestion).
            if (!afterAutoSpace) learnWordBeforeCursor()
        }
        val edit = typographyEdit(text, afterAutoSpace) { connection.getTextBeforeCursor(TypographyLookBehind, 0) }
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

    /**
     * Règles typographiques françaises (§6.3) : modification à faire au lieu d'insérer [text],
     * d'après le texte avant le curseur ([textBefore]). Une espace tapée juste après l'espace
     * ajoutée par une suggestion ([afterAutoSpace]) compte comme un double espace.
     */
    private fun typographyEdit(text: String, afterAutoSpace: Boolean, textBefore: () -> CharSequence?): TextEdit? {
        if (!frenchRulesApply || hasSelection || text.length != 1) return null
        val char = text[0]
        return when {
            char == ' ' && settings.doubleSpacePeriod &&
                (afterAutoSpace || SystemClock.uptimeMillis() - lastSpaceAt < DoubleSpaceMaxDelayMs) ->
                textBefore()?.let(FrenchTypography::doubleSpacePeriod)

            settings.nonBreakingSpace && char in ";:!?" ->
                textBefore()?.let { FrenchTypography.spaceBeforePunctuation(char, it) }

            else -> null
        }
    }

    /**
     * Autocorrection (§7.1) juste avant d'insérer le séparateur [separator] : remplace le mot en
     * cours par la correction retenue et insère le séparateur, règles typographiques comprises.
     * Faux s'il n'y a rien à corriger.
     */
    private fun autocorrectBefore(separator: String): Boolean {
        val connection = currentInputConnection ?: return false
        val engine = engine ?: return false
        val before = connection.getTextBeforeCursor(WordLookBehind, 0) ?: return false
        val word = WordAtCursor.read(before, connection.getTextAfterCursor(1, 0) ?: "") ?: return false
        if (word.word.isEmpty()) return false
        // Le calcul affiché porte presque toujours sur ce mot ; sinon (frappe très rapide), on le refait.
        val computed = lastComputed?.takeIf { it.word == word } ?: computeSuggestions(engine, word, autocorrect = true, rejectedWords)
        val correction = computed.autocorrection ?: return false
        val correctedBefore = before.subSequence(0, before.length - word.word.length).toString() + correction
        val edit = typographyEdit(separator, afterAutoSpace = false) { correctedBefore }?.takeIf { it.deleteBefore == 0 }
        val inserted = correction + (edit?.insert ?: separator)
        connection.beginBatchEdit()
        connection.deleteSurroundingText(word.word.length, 0)
        connection.commitText(inserted, 1)
        connection.endBatchEdit()
        lastAutocorrection = AutocorrectionUndo(typed = word.word, inserted = inserted, separator = inserted.removePrefix(correction))
        lastSpaceAt = if (separator == " ") SystemClock.uptimeMillis() else 0L
        return true
    }

    private fun suggestionsEnabled() = suggestionsApply && settings.suggestions

    /**
     * Apprentissage personnel (§7.3) : seulement là où les suggestions servent, donc jamais en
     * navigation privée ni dans un champ sensible (§3).
     */
    private fun learningEnabled() = suggestionsEnabled()

    /**
     * Le mot avant le curseur est terminé par un séparateur sans avoir été corrigé : s'il est
     * inconnu du dictionnaire embarqué, il compte pour son apprentissage.
     */
    private fun learnWordBeforeCursor() {
        val connection = currentInputConnection ?: return
        val word = WordAtCursor.read(
            connection.getTextBeforeCursor(WordLookBehind, 0) ?: return,
            connection.getTextAfterCursor(1, 0) ?: "",
        )?.word ?: return
        keepTypedWord(word)
    }

    /** [word] tapé est conservé tel quel : appris à la deuxième fois s'il est inconnu (§7.3). */
    private fun keepTypedWord(word: String) {
        val engine = engine ?: return
        if (!learningEnabled() || word.isEmpty() || engine.isInDictionary(word)) return
        val now = System.currentTimeMillis()
        lifecycleScope.launch { personalDictionary.keep(word, now) }
    }

    private fun autocorrectEnabled() = autocorrectApply && settings.suggestions && settings.autocorrect

    /**
     * Recalcule la barre de suggestions pour le mot avant le curseur, sur [suggestDispatcher] :
     * le calcul d'une frappe précédente est annulé, jamais affiché en retard.
     */
    private fun refreshSuggestions() {
        suggestJob?.cancel()
        val engine = engine
        val connection = currentInputConnection
        val word = if (engine != null && connection != null && suggestionsEnabled() && !hasSelection) {
            WordAtCursor.read(
                connection.getTextBeforeCursor(WordLookBehind, 0) ?: "",
                connection.getTextAfterCursor(1, 0) ?: "",
            )
        } else {
            null
        }
        if (engine == null || word == null) {
            lastComputed = null
            suggestionBar = SuggestionBar()
            return
        }
        val autocorrect = autocorrectEnabled()
        val rejected = rejectedWords.toSet()
        suggestJob = lifecycleScope.launch {
            val computed = withContext(suggestDispatcher) { computeSuggestions(engine, word, autocorrect, rejected) }
            lastComputed = computed
            suggestionBar = computed.bar
        }
    }

    override fun pickSuggestion(item: SuggestionItem) {
        val connection = currentInputConnection ?: return
        lastAutocorrection = null
        lastSpaceAt = 0L
        val word = WordAtCursor.read(
            connection.getTextBeforeCursor(WordLookBehind, 0) ?: return,
            connection.getTextAfterCursor(1, 0) ?: "",
        ) ?: return
        // Après une élision (« l' ») ou un mot à compléter, pas d'espace à ajouter derrière « l' ».
        val space = if (item.text.endsWith('\'') || item.text.endsWith('’')) "" else " "
        connection.beginBatchEdit()
        when (item.kind) {
            // Garder le mot tapé : plus de correction pour lui.
            SuggestionItem.Kind.Typed -> {
                rejectedWords += word.word.lowercase()
                keepTypedWord(word.word)
            }
            SuggestionItem.Kind.Prediction -> connection.commitText(item.text, 1)
            SuggestionItem.Kind.Word, SuggestionItem.Kind.Autocorrection -> {
                connection.deleteSurroundingText(word.word.length, 0)
                connection.commitText(item.text, 1)
            }
        }
        connection.commitText(space, 1)
        connection.endBatchEdit()
        autoSpacePending = space.isNotEmpty()
        // Un mot connu choisi dans la barre remonte dans les suggestions (§7.3).
        if (item.kind != SuggestionItem.Kind.Typed && learningEnabled() && engine?.isKnown(item.text) == true) {
            val now = System.currentTimeMillis()
            lifecycleScope.launch { personalDictionary.used(item.text, now) }
        }
    }

    override fun deleteBackward() {
        lastSpaceAt = 0L
        autoSpacePending = false
        val connection = currentInputConnection ?: return
        if (undoAutocorrection(connection)) return
        when {
            hasSelection -> connection.commitText("", 1)
            // Champ vide : certaines apps attendent la touche elle-même (supprimer un destinataire…).
            connection.getTextBeforeCursor(1, 0).isNullOrEmpty() -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            // En points de code, pour ne pas couper un emoji en deux (paire de substitution).
            else -> connection.deleteSurroundingTextInCodePoints(1, 0)
        }
    }

    /**
     * Retour arrière juste après une autocorrection (§7.1) : rétablit le mot tapé, suivi du même
     * séparateur, et ne le corrige plus jusqu'au prochain champ.
     */
    private fun undoAutocorrection(connection: android.view.inputmethod.InputConnection): Boolean {
        val undo = lastAutocorrection ?: return false
        lastAutocorrection = null
        if (hasSelection || connection.getTextBeforeCursor(undo.inserted.length, 0)?.toString() != undo.inserted) return false
        connection.beginBatchEdit()
        connection.deleteSurroundingText(undo.inserted.length, 0)
        connection.commitText(undo.typed + undo.separator, 1)
        connection.endBatchEdit()
        rejectedWords += undo.typed.lowercase()
        keepTypedWord(undo.typed)
        // Même longueur, même curseur : Android ne rappelle pas onUpdateSelection.
        refreshSuggestions()
        return true
    }

    override fun deleteWordBackward() {
        lastAutocorrection = null
        autoSpacePending = false
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
        lastAutocorrection = null
        autoSpacePending = false
        val keyCode = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(abs(steps)) { sendDownUpKeyEvents(keyCode) }
    }

    override fun emojiTyped(emoji: String) {
        lastSpaceAt = 0L
        lastAutocorrection = null
        autoSpacePending = false
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
        lastAutocorrection = null
        autoSpacePending = false
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
        lastAutocorrection = null
        autoSpacePending = false
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

    /** Ce qu'a fait la dernière autocorrection : [typed] remplacé par [inserted] (correction et séparateur). */
    private class AutocorrectionUndo(val typed: String, val inserted: String, val separator: String)

    /** Fin de mot : l'autocorrection s'applique avant (§7.1). */
    private fun isWordSeparator(text: String) = text.length == 1 && text[0] in WordSeparators

    /** Ponctuation qui colle au mot : elle remplace l'espace ajoutée après une suggestion. */
    private fun replacesAutoSpace(text: String) =
        text.length == 1 && (text[0] in ".,…)]" || (text[0] in ";:!?" && !settings.nonBreakingSpace))

    private companion object {
        const val WordSeparators = " .,;:!?…)]»\""

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
