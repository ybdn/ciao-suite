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
import dev.ybdn.ciaocloud.domain.model.EditAvailability
import dev.ybdn.ciaocloud.domain.model.EditCapabilities
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.SaveEditOutcome
import dev.ybdn.ciaocloud.presentation.editor.PhotoEditorScreen
import dev.ybdn.ciaocloud.presentation.editor.SavingDialog
import dev.ybdn.ciaocloud.domain.model.MediaDetails
import dev.ybdn.ciaocloud.presentation.editor.messageRes
import dev.ybdn.ciaocloud.presentation.editor.toast
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.draw.alpha
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.presentation.ciaoCloudViewModel
import dev.ybdn.ciao.designsystem.components.NeoAction
import dev.ybdn.ciao.designsystem.components.NeoActionBar
import dev.ybdn.ciao.designsystem.components.NeoTopBar
import dev.ybdn.ciaocloud.presentation.gallery.DeleteItemsDialog
import dev.ybdn.ciaocloud.presentation.gallery.GalleryEventsEffect
import dev.ybdn.ciaocloud.presentation.gallery.ShareDialogs
import dev.ybdn.ciao.designsystem.theme.NeoTheme
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
    val context = LocalContext.current
    val transferRunning by viewModel.transferRunning.collectAsStateWithLifecycle()
    // Clé de l'élément en cours d'édition (éditeur plein écran), et élément à afficher après une copie.
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var focusKey by rememberSaveable { mutableStateOf<String?>(null) }
    val isBusy by viewModel.actions.isBusy.collectAsStateWithLifecycle()
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val bottomBarHeight = with(LocalDensity.current) { bottomBarHeightPx.toDp() }

    GalleryEventsEffect(viewModel.actions.events)
    ShareDialogs(viewModel.actions)
    val clipboardLocation by viewModel.clipboardLocation.collectAsStateWithLifecycle()
    val metadataSaving by viewModel.metadataSaving.collectAsStateWithLifecycle()
    var metadataEdit by remember { mutableStateOf<Pair<String, MediaDetails?>?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.metadataResults.collect { summary ->
            val message = when {
                summary.cancelled -> null
                summary.ssdUnavailable -> context.getString(R.string.editor_plug_ssd)
                summary.failedNames.isNotEmpty() -> context.getString(R.string.editor_save_failed, summary.failedNames.joinToString())
                summary.modified > 0 -> context.getString(R.string.metadata_saved)
                else -> null
            }
            message?.let { context.toast(it) }
        }
    }
    if (metadataSaving) SavingDialog()

    itemForInfo?.let { infoItem ->
        // Élément à jour (chronologie relue après une modification).
        val item = uiState.items.firstOrNull { it.key == infoItem.key } ?: infoItem
        val capabilities = remember(item, ssdAvailable, transferRunning) { viewModel.editCapabilities(item) }
        InfoSheet(
            item = item,
            loadDetails = viewModel::details,
            onDismiss = { itemForInfo = null },
            metadataAvailability = capabilities.metadata,
            onEditMetadata = { details -> guardAction { metadataEdit = item.key to details } },
            onCopyLocation = viewModel::copyLocation,
        )
    }
    metadataEdit?.let { (key, details) ->
        val item = uiState.items.firstOrNull { it.key == key }
        if (item == null) {
            metadataEdit = null
        } else {
            MetadataEditorSheet(
                details = details,
                clipboardLocation = clipboardLocation,
                onSave = { changes ->
                    metadataEdit = null
                    viewModel.editMetadata(item, changes)
                },
                onDismiss = { metadataEdit = null },
            )
        }
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

        // Copie enregistrée : on l'affiche dès qu'elle apparaît dans la chronologie.
        LaunchedEffect(items, focusKey) {
            val index = focusKey?.let { key -> items.indexOfFirst { it.key == key } } ?: -1
            if (index >= 0) {
                pagerState.scrollToPage(index)
                focusKey = null
            }
        }

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
                val capabilities = remember(item, ssdAvailable, transferRunning) { viewModel.editCapabilities(item) }
                NeoTopBar(
                    title = formatShortDate(item),
                    actions = {
                        EditAction(capabilities) {
                            guardAction {
                                chromeVisible = true
                                editingKey = item.key
                            }
                        }
                    },
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

        val editingItem = editingKey?.let { key -> items.firstOrNull { it.key == key } }
        if (editingItem != null) {
            PhotoEditorScreen(
                item = editingItem,
                onClose = { editingKey = null },
                onSaved = { outcome ->
                    editingKey = null
                    when (outcome) {
                        is SaveEditOutcome.Copied -> {
                            focusKey = outcome.key
                            context.toast(context.getString(R.string.editor_copy_saved))
                        }
                        else -> context.toast(context.getString(R.string.editor_replaced))
                    }
                },
            )
        }
    }
}

/**
 * Crayon « Modifier » : masqué si l'édition est sans objet (vidéo, format), grisé avec un message si
 * elle est momentanément impossible (SSD débranché, transfert en cours).
 */
@Composable
private fun EditAction(capabilities: EditCapabilities, onEdit: () -> Unit) {
    val context = LocalContext.current
    val entry = listOf(capabilities.editCopy, capabilities.replace)
    if (entry.all { it is EditAvailability.Unavailable && it.reason.hidesAction }) return
    val blockedReason = entry.filterIsInstance<EditAvailability.Unavailable>().takeIf { it.size == entry.size }?.first()?.reason
    IconButton(
        onClick = {
            if (blockedReason == null) onEdit() else context.toast(context.getString(blockedReason.messageRes()))
        },
        modifier = Modifier.alpha(if (blockedReason == null) 1f else 0.4f),
    ) {
        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.editor_open))
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
