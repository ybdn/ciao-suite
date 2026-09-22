package dev.ybdn.ciaocloud.presentation.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciaocloud.data.datastore.SettingsDataStore
import dev.ybdn.ciaocloud.domain.model.ThemeMode

/**
 * Racine commune des activités : thème choisi dans les réglages, barres système transparentes aux
 * icônes adaptées au thème de l'app (et non du système).
 */
@Composable
fun ComponentActivity.CiaoCloudContent(
    settingsDataStore: SettingsDataStore,
    content: @Composable () -> Unit,
) {
    // Rien n'est affiché tant que la préférence n'est pas lue (quelques ms) : évite un
    // flash du mauvais thème au démarrage, le fond de fenêtre suit le système entre-temps.
    val themeMode by settingsDataStore.themeMode.collectAsStateWithLifecycle(initialValue = null)
    val darkTheme = when (themeMode ?: return) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    DisposableEffect(darkTheme) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
        )
        onDispose { }
    }

    CiaoCloudTheme(darkTheme = darkTheme, content = content)
}
