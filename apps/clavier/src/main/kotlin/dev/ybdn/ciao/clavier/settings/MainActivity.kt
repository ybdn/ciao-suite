package dev.ybdn.ciao.clavier.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.ybdn.ciao.designsystem.theme.CiaoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CiaoTheme {
                // Deux écrans seulement : pas de bibliothèque de navigation.
                var personalDictionaryOpen by rememberSaveable { mutableStateOf(false) }
                if (personalDictionaryOpen) {
                    PersonalDictionaryScreen(onBack = { personalDictionaryOpen = false })
                } else {
                    OnboardingScreen(onManagePersonalDictionary = { personalDictionaryOpen = true })
                }
            }
        }
    }
}
