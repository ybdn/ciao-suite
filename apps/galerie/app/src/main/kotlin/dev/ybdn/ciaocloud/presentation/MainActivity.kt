package dev.ybdn.ciaocloud.presentation

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciaocloud.CiaoCloudApplication
import dev.ybdn.ciaocloud.domain.model.ThemeMode
import dev.ybdn.ciaocloud.presentation.navigation.CiaoCloudNavHost
import dev.ybdn.ciaocloud.presentation.theme.CiaoCloudTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as CiaoCloudApplication).appContainer

        val deletionLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            appContainer.mediaDeletionRequester.onDeletionResult(result.resultCode)
        }
        appContainer.mediaDeletionRequester.bindLauncher(deletionLauncher)

        setContent {
            // Rien n'est affiché tant que la préférence n'est pas lue (quelques ms) : évite un
            // flash du mauvais thème au démarrage, le fond de fenêtre suit le système entre-temps.
            val themeMode by appContainer.settingsDataStore.themeMode.collectAsStateWithLifecycle(initialValue = null)
            val darkTheme = when (themeMode ?: return@setContent) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Barres système transparentes, icônes adaptées au thème de l'app (et non du système).
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
                onDispose { }
            }

            CiaoCloudTheme(darkTheme = darkTheme) {
                CiaoCloudNavHost()
            }
        }
    }
}
