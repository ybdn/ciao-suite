package dev.ybdn.ciaocloud.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import dev.ybdn.ciaocloud.CiaoCloudApplication
import dev.ybdn.ciaocloud.presentation.navigation.CiaoCloudNavHost
import dev.ybdn.ciaocloud.presentation.theme.CiaoCloudTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as CiaoCloudApplication).appContainer

        val deletionLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            appContainer.mediaDeletionRequester.onDeletionResult(result.resultCode)
        }
        appContainer.mediaDeletionRequester.bindLauncher(deletionLauncher)

        setContent {
            CiaoCloudTheme {
                CiaoCloudNavHost()
            }
        }
    }
}
