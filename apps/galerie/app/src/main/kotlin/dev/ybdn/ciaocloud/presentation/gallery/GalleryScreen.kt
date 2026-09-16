package dev.ybdn.ciaocloud.presentation.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.GalleryFilter
import dev.ybdn.ciaocloud.domain.model.GalleryLocation
import dev.ybdn.ciaocloud.presentation.ciaoCloudViewModel
import dev.ybdn.ciaocloud.presentation.components.NeoButton
import dev.ybdn.ciaocloud.presentation.components.NeoChipRow
import dev.ybdn.ciaocloud.presentation.components.NeoNotice
import dev.ybdn.ciaocloud.presentation.components.NeoTone
import dev.ybdn.ciaocloud.presentation.components.NeoTopBar
import dev.ybdn.ciaocloud.presentation.theme.NeoTheme
import dev.ybdn.ciaocloud.presentation.util.MEDIA_PERMISSIONS
import dev.ybdn.ciaocloud.presentation.util.MediaAccess
import dev.ybdn.ciaocloud.presentation.util.mediaAccess
import dev.ybdn.ciaocloud.presentation.util.openAppSettings

@Composable
fun GalleryScreen(
    onOpenItem: (key: String, filter: GalleryFilter) -> Unit,
) {
    val viewModel = ciaoCloudViewModel { container, app -> GalleryViewModel(container, app) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ssdAvailable by viewModel.ssdAvailable.collectAsStateWithLifecycle()
    val ssdIndexState by viewModel.ssdIndexState.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
        NeoTopBar(stringResource(R.string.gallery_title))

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
                    onOpenItem = { key -> onOpenItem(key, uiState.filter) },
                )
            }
        }
    }
}

@Composable
private fun GalleryGrid(
    entries: List<GalleryEntry>,
    ssdAvailable: Boolean,
    onOpenItem: (String) -> Unit,
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
                        onClick = { onOpenItem(entry.item.key) },
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
