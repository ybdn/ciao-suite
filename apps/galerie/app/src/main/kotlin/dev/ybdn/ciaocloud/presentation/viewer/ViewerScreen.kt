package dev.ybdn.ciaocloud.presentation.viewer

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.presentation.ciaoCloudViewModel
import dev.ybdn.ciaocloud.presentation.components.NeoAction
import dev.ybdn.ciaocloud.presentation.components.NeoActionBar
import dev.ybdn.ciaocloud.presentation.components.NeoTopBar
import dev.ybdn.ciaocloud.presentation.gallery.DeleteItemsDialog
import dev.ybdn.ciaocloud.presentation.gallery.GalleryEventsEffect
import dev.ybdn.ciaocloud.presentation.gallery.ShareDialogs
import dev.ybdn.ciaocloud.presentation.theme.NeoTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Visionneuse plein écran, dans la DA de l'app : barres du haut et du bas identiques à celles des
 * autres écrans, masquables d'un appui (avec les barres système).
 *
 * @param guardAction exécute une action sensible (partage, suppression) ; en mode verrouillé, elle
 * exige d'abord le déverrouillage de l'appareil.
 */
@Composable
fun ViewerScreen(
    source: ViewerSource,
    onBack: () -> Unit,
    guardAction: (action: () -> Unit) -> Unit = { it() },
) {
    val viewModel = ciaoCloudViewModel { container, app -> ViewerViewModel(container, app, source) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ssdAvailable by viewModel.ssdAvailable.collectAsStateWithLifecycle()
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var itemToDelete by remember { mutableStateOf<GalleryItem?>(null) }
    var itemForInfo by remember { mutableStateOf<GalleryItem?>(null) }
    val isBusy by viewModel.actions.isBusy.collectAsStateWithLifecycle()
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val bottomBarHeight = with(LocalDensity.current) { bottomBarHeightPx.toDp() }

    GalleryEventsEffect(viewModel.actions.events)
    ShareDialogs(viewModel.actions)
    itemForInfo?.let { item ->
        InfoSheet(item = item, loadDetails = viewModel::details, onDismiss = { itemForInfo = null })
    }
    itemToDelete?.let { item ->
        DeleteItemsDialog(
            items = listOf(item),
            onDelete = { target ->
                itemToDelete = null
                viewModel.actions.delete(listOf(item), target)
            },
            onDismiss = { itemToDelete = null },
        )
    }

    ImmersiveSystemBars(visible = chromeVisible)
    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize().background(NeoTheme.palette.page)) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        if (uiState.items.isEmpty()) {
            LaunchedEffect(Unit) { onBack() }
            return@Box
        }

        val items = uiState.items
        val pagerState = rememberPagerState(initialPage = uiState.initialIndex) { items.size }
        val currentItem = items.getOrNull(pagerState.currentPage)

        HorizontalPager(
            state = pagerState,
            key = { page -> items.getOrNull(page)?.key ?: page },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            val isCurrentPage = pagerState.settledPage == page
            val originalUri = rememberOriginalUri(item, ssdAvailable, viewModel::originalUri)
            when (item.mediaType) {
                MediaType.PHOTO -> ZoomablePhotoPage(
                    item = item,
                    originalUri = originalUri,
                    isCurrentPage = isCurrentPage,
                    onToggleChrome = { chromeVisible = !chromeVisible },
                )
                MediaType.VIDEO -> VideoPage(
                    item = item,
                    originalUri = originalUri,
                    isCurrentPage = isCurrentPage,
                    chromeVisible = chromeVisible,
                    bottomInset = bottomBarHeight,
                    onToggleChrome = { chromeVisible = !chromeVisible },
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && currentItem != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            currentItem?.let { item ->
                NeoTopBar(
                    title = formatShortDate(item),
                    navigation = {
                        IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.viewer_back),
                                tint = NeoTheme.palette.content,
                            )
                        }
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && currentItem != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomBarHeightPx = it.height },
        ) {
            currentItem?.let { item ->
                val canEdit = viewModel.canEdit(item)
                NeoActionBar(
                    actions = listOfNotNull(
                        NeoAction(
                            Icons.Outlined.Share,
                            stringResource(R.string.gallery_share),
                            onClick = { guardAction { viewModel.actions.share(listOf(item)) } },
                            enabled = !isBusy,
                        ),
                        if (canEdit) {
                            NeoAction(
                                if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                stringResource(R.string.gallery_favorite),
                                onClick = { guardAction { viewModel.actions.toggleFavorite(listOf(item)) } },
                                highlighted = item.isFavorite,
                            )
                        } else {
                            null
                        },
                        NeoAction(Icons.Outlined.Info, stringResource(R.string.gallery_info), onClick = { itemForInfo = item }),
                        if (canEdit) {
                            NeoAction(
                                Icons.Outlined.Delete,
                                stringResource(R.string.gallery_delete),
                                onClick = { guardAction { itemToDelete = item } },
                                enabled = !isBusy,
                            )
                        } else {
                            null
                        },
                    ),
                )
            }
        }
    }
}

private val SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)

/** « 15 sept. 2026 » : tient dans la barre du haut au format des autres écrans. */
private fun formatShortDate(item: GalleryItem): String = SHORT_DATE_FORMATTER.format(item.captureDate)

/** Masque les barres système en même temps que les barres de la visionneuse. */
@Composable
private fun ImmersiveSystemBars(visible: Boolean) {
    val view = LocalView.current
    val activity = LocalContext.current as? Activity ?: return
    val controller = remember(activity, view) { WindowCompat.getInsetsController(activity.window, view) }

    DisposableEffect(controller) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
    LaunchedEffect(visible) {
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
