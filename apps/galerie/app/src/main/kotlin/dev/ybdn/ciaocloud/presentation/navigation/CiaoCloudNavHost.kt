package dev.ybdn.ciaocloud.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.ybdn.ciaocloud.presentation.deleteconfirm.DeleteConfirmationScreen
import dev.ybdn.ciaocloud.presentation.home.HomeScreen
import dev.ybdn.ciaocloud.presentation.progress.ProgressScreen
import dev.ybdn.ciaocloud.presentation.settings.SettingsScreen

object CiaoCloudDestinations {
    const val HOME = "home"
    const val PROGRESS = "progress"
    const val DELETE_CONFIRM = "delete_confirm"
    const val SETTINGS = "settings"
}

@Composable
fun CiaoCloudNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = CiaoCloudDestinations.HOME) {
        composable(CiaoCloudDestinations.HOME) {
            HomeScreen(
                onNavigateToTransfer = { navController.navigate(CiaoCloudDestinations.PROGRESS) },
                onNavigateToSettings = { navController.navigate(CiaoCloudDestinations.SETTINGS) },
            )
        }
        composable(CiaoCloudDestinations.PROGRESS) {
            ProgressScreen(
                onTransferCompleted = { navController.navigate(CiaoCloudDestinations.DELETE_CONFIRM) },
            )
        }
        composable(CiaoCloudDestinations.DELETE_CONFIRM) {
            DeleteConfirmationScreen(
                onCancel = { navController.popBackStack(CiaoCloudDestinations.HOME, inclusive = false) },
                onDeleted = { navController.popBackStack(CiaoCloudDestinations.HOME, inclusive = false) },
            )
        }
        composable(CiaoCloudDestinations.SETTINGS) {
            SettingsScreen()
        }
    }
}
