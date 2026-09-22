package dev.ybdn.ciaocloud.presentation.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Marge de la barre d'état, constante même quand elle est masquée : la visionneuse masque et
 * réaffiche les barres système, et une marge qui suivrait leur visibilité ferait passer les
 * barres de l'app sous les icônes de notification.
 */
@OptIn(ExperimentalLayoutApi::class)
fun Modifier.stableStatusBarsPadding(): Modifier = composed { windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility) }

/** Marge de la barre de navigation système, constante même quand elle est masquée. */
@OptIn(ExperimentalLayoutApi::class)
fun Modifier.stableNavigationBarsPadding(): Modifier = composed { windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility) }
