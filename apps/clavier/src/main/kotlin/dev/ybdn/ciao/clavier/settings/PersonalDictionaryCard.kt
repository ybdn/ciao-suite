package dev.ybdn.ciao.clavier.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciao.clavier.R
import dev.ybdn.ciao.clavier.data.personal.PersonalDictionary
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoCard
import dev.ybdn.ciao.designsystem.components.NeoSectionHeader
import dev.ybdn.ciao.designsystem.components.NeoTone

/** Dictionnaire personnel (apps/clavier/docs/spec-v1.md §7.3, §10.3) : résumé et accès à la liste. */
@Composable
fun PersonalDictionaryCard(index: String, onManage: () -> Unit) {
    val context = LocalContext.current
    val dictionary = remember { PersonalDictionary(context) }
    val learned by dictionary.learned.collectAsStateWithLifecycle(initialValue = emptyList())

    NeoCard {
        NeoSectionHeader(index, stringResource(R.string.personal_title))
        Text(stringResource(R.string.personal_intro), style = MaterialTheme.typography.bodyMedium)
        Text(
            pluralStringResource(R.plurals.personal_count, learned.size, learned.size),
            style = MaterialTheme.typography.titleMedium,
        )
        NeoButton(text = stringResource(R.string.personal_manage), tone = NeoTone.Surface, onClick = onManage)
    }
}
