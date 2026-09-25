package dev.ybdn.ciao.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Palette canonique de neubrutalism.com (https://neubrutalism.com/#anatomy), règles dans
// docs/design-system.md (ADR 0003) : fond crème, encre noire, aplats d'accents, bordures et
// ombres dures sans flou.

/** Encre : texte sur les accents, bordures et ombres du thème clair. */
val Ink = Color(0xFF000000)

// Accents : identiques en clair et en sombre, toujours avec du texte Ink. Chacun a un rôle
// (voir NeoTone) ; un écran en utilise trois au plus.
val Yellow = Color(0xFFFFD23F)

/** « Coral Pink » de la palette canonique. */
val Pink = Color(0xFFFF6B6B)
val Sky = Color(0xFF74B9FF)
val Green = Color(0xFF88D498)
val Orange = Color(0xFFFFA552)
val Lavender = Color(0xFFB8A9FA)

/**
 * Anneau de focus. Le Sky canonique n'a que 2:1 de contraste sur le fond clair : cette variante
 * plus sombre atteint le minimum de 3:1 exigé pour les éléments d'interface (WCAG 1.4.11).
 */
val FocusRing = Color(0xFF2E7BD6)

// Rôles (mêmes teintes que NeoTone.Primary/Selected/…) pour du dessin hors composants Neo* :
// icônes, pistes de curseur, tampons de décision. Pour une action, un état ou un message,
// toujours passer par le rôle plutôt que la teinte (docs/design-system.md, ADR 0003).
val Primary = Yellow
val Selected = Sky
val Danger = Pink
val Success = Green
val Warning = Orange
val Info = Lavender

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
    page = Color(0xFFFFFDF5),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFEFEBE0),
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
