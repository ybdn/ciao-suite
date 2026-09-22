package dev.ybdn.ciao.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Palette néo-brutaliste inspirée de depobudget.com : fond papier, encre quasi noire,
// aplats de couleurs saturées, bordures et ombres dures.

// Couleurs d'accent : identiques en clair et en sombre, toujours avec du texte Ink.
val Ink = Color(0xFF111111)
val Coral = Color(0xFFFF5B45)
val Lime = Color(0xFFC8E832)
val Yellow = Color(0xFFF5B83C)
val Teal = Color(0xFF3CBDB1)
val Sky = Color(0xFF4A9FD4)
val Brick = Color(0xFFD95C5C)

/** Couleurs neutres qui s'inversent entre les thèmes clair et sombre. */
@Immutable
data class NeoPalette(
    val page: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val content: Color,
    /** Bordures et ombres dures. */
    val outline: Color,
)

val LightPalette = NeoPalette(
    page = Color(0xFFFAF7F2),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF6EEDF),
    content = Ink,
    outline = Ink,
)

// En sombre, l'encre devient crème : bordures et ombres claires sur fond presque noir.
val DarkPalette = NeoPalette(
    page = Color(0xFF141311),
    surface = Color(0xFF201E1B),
    surfaceMuted = Color(0xFF2C2823),
    content = Color(0xFFF6EEDF),
    outline = Color(0xFFF6EEDF),
)
