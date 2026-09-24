package dev.ybdn.ciaocloud.presentation.gallery

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.GalleryFilter
import dev.ybdn.ciaocloud.domain.model.GalleryLocation
import dev.ybdn.ciaocloud.presentation.ciaoCloudViewModel
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoChipRow
import dev.ybdn.ciao.designsystem.components.NeoNotice
import dev.ybdn.ciao.designsystem.components.NeoTone
import dev.ybdn.ciao.designsystem.components.NeoTopBar
import dev.ybdn.ciao.designsystem.theme.NeoTheme
import dev.ybdn.ciaocloud.presentation.util.MEDIA_PERMISSIONS
import dev.ybdn.ciaocloud.presentation.util.MediaAccess
import dev.ybdn.ciaocloud.presentation.util.mediaAccess
import dev.ybdn.ciaocloud.presentation.util.openAppSettings

@Composable
fun GalleryScreen(
    onOpenItem: (key: String, filter: GalleryFilter) -> Unit,
    onOpenTrash: () -> Unit,
) {
    val viewModel = ciaoCloudViewModel { container, app -> GalleryViewModel(container, app) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ssdAvailable by viewModel.ssdAvailable.collectAsStateWithLifecycle()
    val ssdIndexState by viewModel.ssdIndexState.collectAsStateWithLifecycle()
    val selectedKeys by viewModel.selectedKeys.collectAsStateWithLifecycle()
    val isBusy by viewModel.actions.isBusy.collectAsStateWithLifecycle()
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showMetadataSheet by rememberSaveable { mutableStateOf(false) }
    val metadataEdit by viewModel.metadataEdit.collectAsStateWithLifecycle()
    val clipboardLocation by viewModel.clipboardLocation.collectAsStateWithLifecycle()
    val selectionMode = selectedKeys.isNotEmpty()

    GalleryEventsEffect(viewModel.actions.events)
    ShareDialogs(viewModel.actions)
    BackHandler(enabled = selectionMode, onBack = viewModel::clearSelection)

    if (showDeleteDialog) {
        DeleteItemsDialog(
            items = remember(selectedKeys) { viewModel.selectedItemsSnapshot() },
            onDelete = { target ->
                showDeleteDialog = false
                viewModel.deleteSelection(target)
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
    val context = LocalContext.current
    val resources = LocalResources.current
    if (showMetadataSheet) {
        GroupMetadataSheet(
            count = selectedKeys.size,
            clipboardLocation = clipboardLocation,
            onApply = { changes ->
                showMetadataSheet = false
                viewModel.previewMetadataEdit(changes)
            },
            onDismiss = { showMetadataSheet = false },
        )
    }
    when (val edit = metadataEdit) {
        is GroupMetadataState.Confirm -> GroupMetadataConfirmDialog(
            preview = edit.preview,
            removesSensitiveData = edit.changes.removeSensitiveData,
            onConfirm = viewModel::applyMetadataEdit,
            onDismiss = viewModel::cancelMetadataEdit,
        )
        is GroupMetadataState.Running -> GroupMetadataProgressDialog(edit.current, edit.total)
        null -> Unit
    }
    LaunchedEffect(viewModel) {
        viewModel.metadataResults.collect { summary ->
            val message = when {
                summary.cancelled -> null
                summary.ssdUnavailable -> resources.getString(R.string.editor_plug_ssd)
                else -> buildList {
                    add(resources.getQuantityString(R.plurals.group_metadata_modified, summary.modified, summary.modified))
                    if (summary.moved > 0) add(resources.getQuantityString(R.plurals.group_metadata_moved, summary.moved, summary.moved))
                    if (summary.skipped > 0) add(resources.getQuantityString(R.plurals.group_metadata_skipped, summary.skipped, summary.skipped))
                    if (summary.failedNames.isNotEmpty()) {
                        add(resources.getQuantityString(R.plurals.group_metadata_failed, summary.failedNames.size, summary.failedNames.size))
                    }
                }.joinToString(", ")
            }
            message?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    var mediaAccess by rememberSaveable { mutableStateOf(context.mediaAccess()) }

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        // Permission accordée ou retirée depuis les paramètres système pendant que l'app était en pause.
        val access = context.mediaAccess()
        if (access != mediaAccess) {
            mediaAccess = access
            viewModel.refresh()
        }
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        mediaAccess = context.mediaAccess()
        viewModel.refresh()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectionMode) {
            NeoTopBar(
                title = pluralStringResource(R.plurals.gallery_selection_count, selectedKeys.size, selectedKeys.size),
                titleStyle = MaterialTheme.typography.titleMedium,
                navigation = {
                    IconButton(onClick = viewModel::clearSelection, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.gallery_selection_close))
                    }
                },
                actions = {
                    if (isBusy) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    IconButton(onClick = viewModel::shareSelection, enabled = !isBusy) {
                        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.gallery_share))
                    }
                    IconButton(onClick = { showMetadataSheet = true }, enabled = !isBusy) {
                        Icon(Icons.Outlined.EditNote, contentDescription = stringResource(R.string.metadata_edit))
                    }
                    IconButton(onClick = viewModel::toggleFavoriteOnSelection) {
                        Icon(Icons.Outlined.FavoriteBorder, contentDescription = stringResource(R.string.gallery_favorite))
                    }
                    IconButton(onClick = { showDeleteDialog = true }, enabled = !isBusy) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.gallery_delete))
                    }
                },
            )
        } else {
            NeoTopBar(
                title = stringResource(R.string.gallery_title),
                actions = {
                    IconButton(onClick = onOpenTrash) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.gallery_trash))
                    }
                },
            )
        }

        val filters = GalleryFilter.entries
        NeoChipRow(
            options = filters.map { stringResource(it.labelRes()) },
            selectedIndex = filters.indexOf(uiState.filter),
            onSelect = { viewModel.onFilterSelected(filters[it]) },
        )

        if (mediaAccess != MediaAccess.FULL) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NeoNotice(
                    stringResource(
                        if (mediaAccess == MediaAccess.PARTIAL) R.string.gallery_permission_partial else R.string.gallery_permission_needed,
                    ),
                    tone = NeoTone.Yellow,
                )
                NeoButton(
                    text = stringResource(R.string.gallery_permission_grant),
                    onClick = { permissionLauncher.launch(MEDIA_PERMISSIONS) },
                    tone = NeoTone.Yellow,
                )
                NeoButton(
                    text = stringResource(R.string.home_open_app_settings),
                    onClick = context::openAppSettings,
                    tone = NeoTone.Surface,
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.entries.isEmpty() -> Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(
                            if (uiState.totalCount == 0) R.string.gallery_empty else R.string.gallery_empty_filter,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = NeoTheme.palette.content,
                    )
                    if (uiState.totalCount == 0) {
                        SsdIndexControls(
                            state = ssdIndexState,
                            onRefresh = viewModel::refreshSsdIndex,
                        )
                    }
                }
                else -> GalleryGrid(
                    entries = uiState.entries,
                    ssdAvailable = ssdAvailable,
                    selectedKeys = selectedKeys,
                    onOpenItem = { key ->
                        if (selectionMode) viewModel.toggleSelection(key) else onOpenItem(key, uiState.filter)
                    },
                    onLongPressItem = viewModel::toggleSelection,
                )
            }
        }
    }
}

@Composable
private fun GalleryGrid(
    entries: List<GalleryEntry>,
    ssdAvailable: Boolean,
    selectedKeys: Set<String>,
    onOpenItem: (String) -> Unit,
    onLongPressItem: (String) -> Unit,
) {
    val gridState = rememberLazyGridState()
    var columns by rememberSaveable { mutableIntStateOf(DEFAULT_COLUMNS) }

    // La grille garde sa position sur la clé du premier élément visible : si des médias plus récents
    // arrivent avant que l'utilisateur ait défilé (sources chargées l'une après l'autre), on revient en haut.
    var hasUserScrolled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) hasUserScrolled = true
    }
    val firstKey = entries.firstOrNull()?.key
    LaunchedEffect(firstKey) {
        if (!hasUserScrolled) gridState.scrollToItem(0)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
            verticalArrangement = Arrangement.spacedBy(TILE_SPACING),
            modifier = Modifier
                .fillMaxSize()
                .pinchToChangeColumns(
                    onZoomIn = { columns = MIN_COLUMNS },
                    onZoomOut = { columns = MAX_COLUMNS },
                ),
        ) {
            items(
                count = entries.size,
                key = { entries[it].key },
                span = { index -> GridItemSpan(if (entries[index] is GalleryEntry.DayHeader) maxLineSpan else 1) },
                contentType = { index -> if (entries[index] is GalleryEntry.DayHeader) "header" else "media" },
            ) { index ->
                when (val entry = entries[index]) {
                    is GalleryEntry.DayHeader -> DayHeader(entry)
                    is GalleryEntry.Media -> GalleryTile(
                        item = entry.item,
                        dimmed = !ssdAvailable && entry.item.location == GalleryLocation.SSD,
                        selected = if (selectedKeys.isEmpty()) null else entry.item.key in selectedKeys,
                        onClick = { onOpenItem(entry.item.key) },
                        onLongClick = { onLongPressItem(entry.item.key) },
                    )
                }
            }
        }

        FastScroller(
            gridState = gridState,
            labelForIndex = { index ->
                when (val entry = entries.getOrNull(index)) {
                    is GalleryEntry.DayHeader -> formatMonth(entry.date)
                    is GalleryEntry.Media -> formatMonth(entry.item.captureDate)
                    null -> ""
                }
            },
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun DayHeader(header: GalleryEntry.DayHeader) {
    Text(
        text = formatDay(header.date),
        style = MaterialTheme.typography.titleMedium,
        color = NeoTheme.palette.content,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
    )
}

private fun GalleryFilter.labelRes(): Int = when (this) {
    GalleryFilter.ALL -> R.string.gallery_filter_all
    GalleryFilter.FAVORITES -> R.string.gallery_filter_favorites
    GalleryFilter.NOT_BACKED_UP -> R.string.gallery_filter_not_backed_up
    GalleryFilter.VIDEOS -> R.string.gallery_filter_videos
}

private const val DEFAULT_COLUMNS = 3
private const val MIN_COLUMNS = 3
private const val MAX_COLUMNS = 5
private val TILE_SPACING = 2.dp
