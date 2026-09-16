package dev.ybdn.ciaocloud.presentation.navigation

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.GalleryFilter
import dev.ybdn.ciaocloud.presentation.components.NeoBottomBar
import dev.ybdn.ciaocloud.presentation.components.NeoNavItem
import dev.ybdn.ciaocloud.presentation.deleteconfirm.DeleteConfirmationScreen
import dev.ybdn.ciaocloud.presentation.gallery.GalleryScreen
import dev.ybdn.ciaocloud.presentation.home.HomeScreen
import dev.ybdn.ciaocloud.presentation.progress.ProgressScreen
import dev.ybdn.ciaocloud.presentation.settings.SettingsScreen
import dev.ybdn.ciaocloud.presentation.theme.NeoTheme
import dev.ybdn.ciaocloud.presentation.viewer.ViewerScreen

object CiaoCloudDestinations {
    const val GALLERY = "gallery"
    const val HOME = "home"
    const val PROGRESS = "progress"
    const val DELETE_CONFIRM = "delete_confirm"
    const val SETTINGS = "settings"
    const val VIEWER = "viewer/{key}?filter={filter}"

    fun viewer(key: String, filter: GalleryFilter): String = "viewer/${Uri.encode(key)}?filter=${filter.name}"
}

@Composable
fun CiaoCloudNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabs = listOf(
        NeoNavItem(CiaoCloudDestinations.GALLERY, stringResource(R.string.tab_photos), Icons.Outlined.PhotoLibrary),
        NeoNavItem(CiaoCloudDestinations.HOME, stringResource(R.string.tab_offload), Icons.Outlined.Upload),
        NeoNavItem(CiaoCloudDestinations.SETTINGS, stringResource(R.string.tab_settings), Icons.Outlined.Settings),
    )

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == currentRoute }) {
                NeoBottomBar(items = tabs, selectedRoute = currentRoute) { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
        containerColor = NeoTheme.palette.page,
        // Chaque écran gère ses propres marges système ; seule la barre basse est retranchée ici.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = CiaoCloudDestinations.GALLERY,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(CiaoCloudDestinations.GALLERY) {
                GalleryScreen(
                    onOpenItem = { key, filter -> navController.navigate(CiaoCloudDestinations.viewer(key, filter)) },
                )
            }
            composable(
                route = CiaoCloudDestinations.VIEWER,
                arguments = listOf(
                    navArgument("key") { type = NavType.StringType },
                    navArgument("filter") {
                        type = NavType.StringType
                        defaultValue = GalleryFilter.ALL.name
                    },
                ),
            ) { entry ->
                ViewerScreen(
                    initialKey = entry.arguments?.getString("key").orEmpty(),
                    filter = GalleryFilter.valueOf(entry.arguments?.getString("filter") ?: GalleryFilter.ALL.name),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(CiaoCloudDestinations.HOME) {
                HomeScreen(
                    onNavigateToTransfer = { navController.navigate(CiaoCloudDestinations.PROGRESS) },
                    onNavigateToDeleteConfirm = { navController.navigate(CiaoCloudDestinations.DELETE_CONFIRM) },
                )
            }
            composable(CiaoCloudDestinations.PROGRESS) {
                ProgressScreen(
                    // L'écran de progression est retiré de la pile : un retour arrière depuis la
                    // confirmation ramène à l'accueil, jamais sur un écran de transfert terminé.
                    onNavigateToDeleteConfirm = {
                        navController.navigate(CiaoCloudDestinations.DELETE_CONFIRM) {
                            popUpTo(CiaoCloudDestinations.HOME)
                        }
                    },
                    onBackToHome = { navController.popBackStack(CiaoCloudDestinations.HOME, inclusive = false) },
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
}
