package dev.ybdn.ciaocloud.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.ybdn.ciaocloud.CiaoCloudApplication
import dev.ybdn.ciaocloud.presentation.navigation.CiaoCloudNavHost
import dev.ybdn.ciaocloud.presentation.theme.CiaoCloudContent

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as CiaoCloudApplication).appContainer
        appContainer.intentSenderLauncher.register(this)

        setContent {
            CiaoCloudContent(appContainer.settingsDataStore) {
                CiaoCloudNavHost()
            }
        }
    }
}
