package dev.ybdn.ciao.clavier.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciao.clavier.data.AppearancePreferences
import dev.ybdn.ciao.clavier.domain.appearance.AppearanceSettings
import dev.ybdn.ciao.clavier.domain.appearance.resolveDarkTheme
import dev.ybdn.ciao.designsystem.theme.CiaoTheme

/** Écrans de l'app de réglages : pas de bibliothèque de navigation pour si peu. */
private enum class Screen { Onboarding, PersonalDictionary, About }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val appearancePreferences = remember { AppearancePreferences(context) }
            val appearance by appearancePreferences.settings.collectAsStateWithLifecycle(initialValue = AppearanceSettings())
            // Mode Système (§5.3) : s'applique aussi à l'app de réglages, pas seulement au clavier.
            val darkTheme = appearance.themeMode.resolveDarkTheme(systemDark = isSystemInDarkTheme())
            CiaoTheme(darkTheme = darkTheme) {
                var screen by rememberSaveable { mutableStateOf(Screen.Onboarding) }
                when (screen) {
                    Screen.Onboarding -> OnboardingScreen(
                        onManagePersonalDictionary = { screen = Screen.PersonalDictionary },
                        onOpenAbout = { screen = Screen.About },
                    )
                    Screen.PersonalDictionary -> PersonalDictionaryScreen(onBack = { screen = Screen.Onboarding })
                    Screen.About -> AboutScreen(onBack = { screen = Screen.Onboarding })
                }
            }
        }
    }
}
