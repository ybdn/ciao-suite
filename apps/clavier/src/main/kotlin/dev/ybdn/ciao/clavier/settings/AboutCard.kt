package dev.ybdn.ciao.clavier.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.ybdn.ciao.clavier.R
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoCard
import dev.ybdn.ciao.designsystem.components.NeoSectionHeader
import dev.ybdn.ciao.designsystem.components.NeoTone

/** Entrée vers l'écran « À propos » (apps/clavier/docs/spec-v1.md §10.4). */
@Composable
fun AboutCard(index: String, onOpen: () -> Unit) {
    NeoCard {
        NeoSectionHeader(index, stringResource(R.string.about_title))
        NeoButton(text = stringResource(R.string.about_open), tone = NeoTone.Surface, onClick = onOpen)
    }
}
