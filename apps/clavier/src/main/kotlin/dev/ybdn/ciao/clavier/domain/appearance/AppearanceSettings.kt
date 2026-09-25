package dev.ybdn.ciao.clavier.domain.appearance

/** Thème du clavier et de l'app de réglages (apps/clavier/docs/spec-v1.md §5.3, §10.2). */
enum class ThemeMode {
    System,
    Light,
    Dark,
}

/**
 * Hauteur du clavier (apps/clavier/docs/spec-v1.md §10.2) : [scale] multiplie les dimensions
 * calées sur Gboard (§5.1), qui restent la référence pour la taille Normale.
 */
enum class KeyboardHeight(val scale: Float) {
    Compact(0.85f),
    Standard(1f),
    Tall(1.15f),
}

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val keyboardHeight: KeyboardHeight = KeyboardHeight.Standard,
)

/**
 * Thème sombre à appliquer d'après ce réglage et [systemDark] (thème sombre du système, lu par
 * l'appelant avec `isSystemInDarkTheme()`). En mode Système, le clavier suit le système en direct.
 */
fun ThemeMode.resolveDarkTheme(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.System -> systemDark
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}
