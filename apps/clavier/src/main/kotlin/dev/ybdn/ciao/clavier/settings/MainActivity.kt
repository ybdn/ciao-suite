package dev.ybdn.ciao.clavier.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.ybdn.ciao.designsystem.theme.CiaoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CiaoTheme {
                OnboardingScreen()
            }
        }
    }
}
