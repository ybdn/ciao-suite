package dev.ybdn.ciao.galerie.presentation.navigation

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Style
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
import dev.ybdn.ciao.galerie.R
import dev.ybdn.ciao.galerie.domain.model.GalleryFilter
import dev.ybdn.ciao.designsystem.components.NeoBottomBar
import dev.ybdn.ciao.designsystem.components.NeoNavItem
import dev.ybdn.ciao.galerie.presentation.deleteconfirm.DeleteConfirmationScreen
import dev.ybdn.ciao.galerie.presentation.gallery.GalleryScreen
import dev.ybdn.ciao.galerie.presentation.home.HomeScreen
import dev.ybdn.ciao.galerie.presentation.progress.ProgressScreen
import dev.ybdn.ciao.galerie.presentation.settings.SettingsScreen
import dev.ybdn.ciao.designsystem.theme.NeoTheme
import dev.ybdn.ciao.galerie.presentation.trash.TrashScreen
import dev.ybdn.ciao.galerie.presentation.triage.TriageScreen
import dev.ybdn.ciao.galerie.presentation.triageconfirm.TriageConfirmScreen
import dev.ybdn.ciao.galerie.presentation.viewer.ViewerScreen
import dev.ybdn.ciao.galerie.presentation.viewer.ViewerSource

object GalerieDestinations {
    const val GALLERY = "gallery"
    const val HOME = "home"
    const val PROGRESS = "progress"
    const val DELETE_CONFIRM = "delete_confirm"
    const val SETTINGS = "settings"
    const val TRASH = "trash"
    const val TRIAGE = "triage"
    const val TRIAGE_CONFIRM = "triage_confirm"
    const val VIEWER = "viewer/{key}?filter={filter}"

    fun viewer(key: String, filter: GalleryFilter): String = "viewer/${Uri.encode(key)}?filter=${filter.name}"
}

@Composable
fun GalerieNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabs = listOf(
        NeoNavItem(GalerieDestinations.GALLERY, stringResource(R.string.tab_photos), Icons.Outlined.PhotoLibrary),
        NeoNavItem(GalerieDestinations.TRIAGE, stringResource(R.string.tab_triage), Icons.Outlined.Style),
        NeoNavItem(GalerieDestinations.HOME, stringResource(R.string.tab_offload), Icons.Outlined.Upload),
        NeoNavItem(GalerieDestinations.SETTINGS, stringResource(R.string.tab_settings), Icons.Outlined.Settings),
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
            startDestination = GalerieDestinations.GALLERY,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(GalerieDestinations.GALLERY) {
                GalleryScreen(
                    onOpenItem = { key, filter -> navController.navigate(GalerieDestinations.viewer(key, filter)) },
                    onOpenTrash = { navController.navigate(GalerieDestinations.TRASH) },
                )
            }
            composable(GalerieDestinations.TRIAGE) {
                TriageScreen(onOpenDeletionQueue = { navController.navigate(GalerieDestinations.TRIAGE_CONFIRM) })
            }
            composable(GalerieDestinations.TRIAGE_CONFIRM) {
                TriageConfirmScreen(
                    onBack = { navController.popBackStack() },
                    onOpenItem = { key -> navController.navigate(GalerieDestinations.viewer(key, GalleryFilter.ALL)) },
                )
            }
            composable(GalerieDestinations.TRASH) {
                TrashScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = GalerieDestinations.VIEWER,
                arguments = listOf(
                    navArgument("key") { type = NavType.StringType },
                    navArgument("filter") {
                        type = NavType.StringType
                        defaultValue = GalleryFilter.ALL.name
                    },
                ),
            ) { entry ->
                ViewerScreen(
                    source = ViewerSource.Timeline(
                        initialKey = entry.arguments?.getString("key").orEmpty(),
                        filter = GalleryFilter.valueOf(entry.arguments?.getString("filter") ?: GalleryFilter.ALL.name),
                    ),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(GalerieDestinations.HOME) {
                HomeScreen(
                    onNavigateToTransfer = { navController.navigate(GalerieDestinations.PROGRESS) },
                    onNavigateToDeleteConfirm = { navController.navigate(GalerieDestinations.DELETE_CONFIRM) },
                )
            }
            composable(GalerieDestinations.PROGRESS) {
                ProgressScreen(
                    // L'écran de progression est retiré de la pile : un retour arrière depuis la
                    // confirmation ramène à l'accueil, jamais sur un écran de transfert terminé.
                    onNavigateToDeleteConfirm = {
                        navController.navigate(GalerieDestinations.DELETE_CONFIRM) {
                            popUpTo(GalerieDestinations.HOME)
                        }
                    },
                    onBackToHome = { navController.popBackStack(GalerieDestinations.HOME, inclusive = false) },
                )
            }
            composable(GalerieDestinations.DELETE_CONFIRM) {
                DeleteConfirmationScreen(
                    onCancel = { navController.popBackStack(GalerieDestinations.HOME, inclusive = false) },
                    onDeleted = { navController.popBackStack(GalerieDestinations.HOME, inclusive = false) },
                )
            }
            composable(GalerieDestinations.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}
