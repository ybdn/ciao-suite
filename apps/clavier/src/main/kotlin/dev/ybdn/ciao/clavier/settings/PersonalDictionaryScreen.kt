package dev.ybdn.ciao.clavier.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciao.clavier.R
import dev.ybdn.ciao.clavier.data.personal.PersonalDictionary
import dev.ybdn.ciao.clavier.domain.suggest.PersonalDictionaryRules
import dev.ybdn.ciao.clavier.domain.suggest.PersonalWord
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoCard
import dev.ybdn.ciao.designsystem.components.NeoKey
import dev.ybdn.ciao.designsystem.components.NeoNotice
import dev.ybdn.ciao.designsystem.components.NeoScreen
import dev.ybdn.ciao.designsystem.components.NeoTextField
import dev.ybdn.ciao.designsystem.components.NeoTone
import kotlinx.coroutines.launch

/**
 * Gestion du dictionnaire personnel (apps/clavier/docs/spec-v1.md §7.3) : liste des mots appris,
 * recherche, suppression unitaire, ajout manuel, effacement total.
 */
@Composable
fun PersonalDictionaryScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val dictionary = remember { PersonalDictionary(context) }
    val learned by dictionary.learned.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var newWord by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    fun add() {
        val word = PersonalDictionaryRules.normalizeManual(newWord)
        invalid = word == null
        if (word == null) return
        newWord = ""
        scope.launch { dictionary.add(word, System.currentTimeMillis()) }
    }

    NeoScreen(
        title = stringResource(R.string.personal_title),
        navigation = {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.personal_back))
            }
        },
    ) {
        NeoCard {
            NeoTextField(
                label = stringResource(R.string.personal_add_label),
                value = newWord,
                onValueChange = {
                    newWord = it
                    invalid = false
                },
                placeholder = stringResource(R.string.personal_add_placeholder),
                error = if (invalid) stringResource(R.string.personal_add_invalid) else null,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { add() }),
            )
            NeoButton(text = stringResource(R.string.personal_add_action), enabled = newWord.isNotBlank(), onClick = ::add)
        }

        if (learned.isEmpty()) {
            NeoNotice(stringResource(R.string.personal_empty), tone = NeoTone.Info)
        } else {
            NeoTextField(
                label = stringResource(R.string.personal_search_label),
                value = query,
                onValueChange = { query = it },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
            )
            val shown = learned.filter { PersonalDictionaryRules.matches(it.word, query) }
            if (shown.isEmpty()) {
                NeoNotice(stringResource(R.string.personal_no_match), tone = NeoTone.Info)
            } else {
                NeoCard {
                    shown.forEach { word ->
                        PersonalWordRow(word, onDelete = { scope.launch { dictionary.delete(word.word) } })
                    }
                }
            }
            ConfirmingDangerButton(
                text = stringResource(R.string.personal_clear),
                confirmText = stringResource(R.string.personal_clear_confirm),
                enabled = true,
                onConfirm = { scope.launch { dictionary.clear() } },
            )
        }
    }
}

@Composable
private fun PersonalWordRow(word: PersonalWord, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(word.word, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        NeoKey(onClick = onDelete, modifier = Modifier.size(DeleteButtonSize), tone = NeoTone.Muted) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.personal_delete, word.word),
                modifier = Modifier.size(DeleteIconSize),
            )
        }
    }
}

private val DeleteButtonSize = 40.dp
private val DeleteIconSize = 18.dp
