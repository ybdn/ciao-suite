package dev.ybdn.ciaocloud.presentation.viewer

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.graphics.vector.ImageVector
import dev.ybdn.ciaocloud.presentation.gallery.DeleteItemsDialog
import dev.ybdn.ciaocloud.presentation.gallery.GalleryEventsEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.GalleryFilter
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.presentation.ciaoCloudViewModel
import dev.ybdn.ciaocloud.presentation.gallery.formatDay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Visionneuse plein écran : fond noir quel que soit le thème, barres système masquables d'un appui. */
@Composable
fun ViewerScreen(
    initialKey: String,
    filter: GalleryFilter,
    onBack: () -> Unit,
) {
    val viewModel = ciaoCloudViewModel { container, app -> ViewerViewModel(container, app, initialKey, filter) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ssdAvailable by viewModel.ssdAvailable.collectAsStateWithLifecycle()
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var itemToDelete by remember { mutableStateOf<GalleryItem?>(null) }
    val isBusy by viewModel.actions.isBusy.collectAsStateWithLifecycle()

    GalleryEventsEffect(viewModel.actions.events)
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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (uiState.isLoading) return@Box
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
            currentItem?.let { ViewerTopBar(item = it, onBack = onBack) }
        }

        AnimatedVisibility(
            visible = chromeVisible && currentItem != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            currentItem?.let { item ->
                ViewerActionBar(
                    item = item,
                    isBusy = isBusy,
                    onShare = { viewModel.actions.share(listOf(item)) },
                    onToggleFavorite = { viewModel.actions.toggleFavorite(listOf(item)) },
                    onDelete = { itemToDelete = item },
                )
            }
        }
    }
}

@Composable
private fun ViewerTopBar(item: GalleryItem, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.viewer_back), tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatDay(item.captureDate),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(item.sortEpochMillis?.let(::formatTime), item.displayName).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ViewerActionBar(
    item: GalleryItem,
    isBusy: Boolean,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
            .navigationBarsPadding()
            .height(ACTION_BAR_RESERVED_HEIGHT),
    ) {
        ViewerAction(Icons.Outlined.Share, stringResource(R.string.gallery_share), onShare, enabled = !isBusy)
        ViewerAction(
            if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            stringResource(R.string.gallery_favorite),
            onToggleFavorite,
        )
        ViewerAction(Icons.Outlined.Delete, stringResource(R.string.gallery_delete), onDelete, enabled = !isBusy)
    }
}

@Composable
private fun ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

/** Fond dégradé du bas de la visionneuse, sous les contrôles vidéo et la barre d'actions. */
@Composable
fun ViewerBottomBarContainer(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) { content() }
}

/** Masque les barres système quand l'interface est masquée ; icônes claires sur fond noir. */
@Composable
private fun ImmersiveSystemBars(visible: Boolean) {
    val view = LocalView.current
    val activity = LocalContext.current as? Activity ?: return
    val controller = remember(activity, view) { WindowCompat.getInsetsController(activity.window, view) }

    DisposableEffect(controller) {
        val lightStatusBars = controller.isAppearanceLightStatusBars
        val lightNavigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = lightStatusBars
            controller.isAppearanceLightNavigationBars = lightNavigationBars
        }
    }
    LaunchedEffect(visible) {
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)

private fun formatTime(epochMillis: Long): String =
    TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
