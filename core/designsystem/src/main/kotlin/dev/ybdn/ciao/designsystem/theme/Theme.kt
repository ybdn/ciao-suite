package dev.ybdn.ciao.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalNeoPalette = staticCompositionLocalOf { LightPalette }

object NeoTheme {
    val palette: NeoPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalNeoPalette.current
}

@Composable
fun CiaoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    // Le schéma Material ne sert qu'aux composants Material restants (indicateurs de chargement,
    // couleur de texte par défaut) : il reprend la palette néo-brutaliste.
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.content, onPrimary = palette.page,
            background = palette.page, onBackground = palette.content,
            surface = palette.page, onSurface = palette.content,
            outline = palette.outline, error = Brick, onError = Ink,
        )
    } else {
        lightColorScheme(
            primary = palette.content, onPrimary = palette.page,
            background = palette.page, onBackground = palette.content,
            surface = palette.page, onSurface = palette.content,
            outline = palette.outline, error = Brick, onError = Ink,
        )
    }
    CompositionLocalProvider(LocalNeoPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CiaoTypography,
            content = content,
        )
    }
}
