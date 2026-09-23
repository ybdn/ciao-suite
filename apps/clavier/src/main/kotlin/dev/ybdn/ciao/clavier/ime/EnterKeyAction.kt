package dev.ybdn.ciao.clavier.ime

import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import dev.ybdn.ciao.clavier.R

/**
 * Action de la touche Entrée, d'après `imeOptions` du champ actif (apps/clavier/docs/spec-v1.md
 * §6.1). La maquette n'a qu'une icône de flèche : seule la description TalkBack change ici ; une
 * icône par action reste à dessiner.
 */
enum class EnterKeyAction(@StringRes val descriptionRes: Int) {
    Send(R.string.key_enter_send),
    Search(R.string.key_enter_search),
    Next(R.string.key_enter_next),
    Done(R.string.key_enter_done),
    Go(R.string.key_enter_go),
    Newline(R.string.key_enter_newline),
}

fun EditorInfo?.enterKeyAction(): EnterKeyAction {
    val action = (this?.imeOptions ?: EditorInfo.IME_ACTION_UNSPECIFIED) and EditorInfo.IME_MASK_ACTION
    return when (action) {
        EditorInfo.IME_ACTION_SEND -> EnterKeyAction.Send
        EditorInfo.IME_ACTION_SEARCH -> EnterKeyAction.Search
        EditorInfo.IME_ACTION_NEXT -> EnterKeyAction.Next
        EditorInfo.IME_ACTION_DONE -> EnterKeyAction.Done
        EditorInfo.IME_ACTION_GO -> EnterKeyAction.Go
        else -> EnterKeyAction.Newline
    }
}
