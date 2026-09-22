package dev.ybdn.ciao.clavier.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ybdn.ciao.clavier.R
import dev.ybdn.ciao.designsystem.components.BorderWidth
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoNotice
import dev.ybdn.ciao.designsystem.theme.NeoTheme

/**
 * Squelette du lot 1 : prouve que Compose tourne dans le service et parle à l'[InputConnection][
 * android.view.inputmethod.InputConnection] du champ actif. La disposition AZERTY complète
 * (touches, accents, pages) arrive au lot 2 (apps/clavier/docs/spec-v1.md §6, §12).
 */
@Composable
fun KeyboardPlaceholder(onInsertSpace: () -> Unit) {
    val palette = NeoTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.page)
            .border(BorderWidth, palette.outline)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NeoNotice(stringResource(R.string.ime_placeholder))
        NeoButton(
            text = stringResource(R.string.ime_placeholder_space),
            onClick = onInsertSpace,
        )
    }
}
