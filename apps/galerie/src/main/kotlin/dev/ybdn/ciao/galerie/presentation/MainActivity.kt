package dev.ybdn.ciao.galerie.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.ybdn.ciao.galerie.GalerieApplication
import dev.ybdn.ciao.galerie.presentation.navigation.GalerieNavHost
import dev.ybdn.ciao.galerie.presentation.theme.GalerieContent

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as GalerieApplication).appContainer
        appContainer.intentSenderLauncher.register(this)

        setContent {
            GalerieContent(appContainer.settingsDataStore) {
                GalerieNavHost()
            }
        }
    }
}
