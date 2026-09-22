package dev.ybdn.ciaocloud.presentation.triageconfirm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.TriageDeletionPlan
import dev.ybdn.ciaocloud.presentation.ciaoCloudViewModel
import dev.ybdn.ciao.designsystem.components.BorderWidth
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoCard
import dev.ybdn.ciao.designsystem.components.NeoNotice
import dev.ybdn.ciao.designsystem.components.NeoTone
import dev.ybdn.ciao.designsystem.components.NeoTopBar
import dev.ybdn.ciao.designsystem.components.stableNavigationBarsPadding
import dev.ybdn.ciaocloud.presentation.gallery.GalleryEventsEffect
import dev.ybdn.ciaocloud.presentation.gallery.GalleryTile
import dev.ybdn.ciao.designsystem.theme.NeoTheme
import dev.ybdn.ciaocloud.presentation.util.formatBytes

/**
 * Validation groupée de la file d'attente du tri, distincte de la confirmation du délestage :
 * médias du téléphone vers la corbeille système, médias du SSD seul effacés définitivement.
 */
@Composable
fun TriageConfirmScreen(
    onBack: () -> Unit,
    onOpenItem: (key: String) -> Unit,
) {
    val viewModel = ciaoCloudViewModel { container, app -> TriageConfirmViewModel(container, app) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ssdAvailable by viewModel.ssdAvailable.collectAsStateWithLifecycle()
    var showSsdConfirm by rememberSaveable { mutableStateOf(false) }
    val plan = uiState.plan

    GalleryEventsEffect(viewModel.events)
    LaunchedEffect(viewModel) { viewModel.finished.collect { onBack() } }
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    if (showSsdConfirm) {
        SsdDeletionDialog(
            count = plan.ssdDeletionCount,
            onConfirm = {
                showSsdConfirm = false
                viewModel.confirmDeletion()
            },
            onDismiss = { showSsdConfirm = false },
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(NeoTheme.palette.page)) {
        NeoTopBar(
            title = stringResource(R.string.triage_confirm_title),
            titleStyle = MaterialTheme.typography.titleLarge,
            navigation = {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.viewer_back))
                }
            },
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                uiState.isLoading -> CircularProgressIndicator()
                plan.isEmpty -> Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    NeoNotice(stringResource(R.string.triage_confirm_empty), tone = NeoTone.Muted)
                    NeoButton(stringResource(R.string.triage_back), onClick = onBack, tone = NeoTone.Surface)
                }
                else -> QueueGrid(
                    plan = plan,
                    ssdAvailable = ssdAvailable,
                    enabled = !uiState.isDeleting,
                    onOpenItem = onOpenItem,
                    onDequeue = viewModel::dequeue,
                )
            }
        }

        if (!plan.isEmpty) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NeoTheme.palette.page)
                    .stableNavigationBarsPadding(),
            ) {
                HorizontalDivider(thickness = BorderWidth, color = NeoTheme.palette.outline)
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (uiState.isDeleting) CircularProgressIndicator()
                    NeoButton(
                        text = pluralStringResource(
                            R.plurals.triage_confirm_button,
                            plan.all.size,
                            plan.all.size,
                            formatBytes(plan.totalBytes),
                        ),
                        onClick = {
                            // Toute suppression du SSD, définitive, demande une seconde confirmation explicite.
                            if (plan.ssdDeletionCount > 0) showSsdConfirm = true else viewModel.confirmDeletion()
                        },
                        tone = NeoTone.Brick,
                        enabled = !uiState.isDeleting,
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueGrid(
    plan: TriageDeletionPlan,
    ssdAvailable: Boolean,
    enabled: Boolean,
    onOpenItem: (String) -> Unit,
    onDequeue: (GalleryItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
        verticalArrangement = Arrangement.spacedBy(TILE_SPACING),
        modifier = Modifier.fillMaxSize(),
    ) {
        fullWidth("intro") {
            Text(
                stringResource(R.string.triage_confirm_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = NeoTheme.palette.content,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
        }

        if (plan.phone.isNotEmpty()) {
            fullWidth("phone-header") {
                SectionHeader(
                    title = pluralStringResource(R.plurals.triage_confirm_phone_title, plan.phone.size, plan.phone.size),
                    hint = stringResource(R.string.gallery_delete_phone_hint),
                ) {
                    if (plan.notBackedUpCount > 0) {
                        NeoNotice(
                            pluralStringResource(R.plurals.gallery_delete_not_backed_up, plan.notBackedUpCount, plan.notBackedUpCount),
                        )
                    }
                    if (plan.backedUpCount > 0) {
                        NeoNotice(
                            pluralStringResource(R.plurals.triage_confirm_backed_up_copies, plan.backedUpCount, plan.backedUpCount),
                            tone = NeoTone.Coral,
                        )
                    }
                }
            }
            queueTiles(plan.phone, ssdAvailable = true, enabled, onOpenItem, onDequeue)
        }

        if (plan.ssdOnly.isNotEmpty()) {
            fullWidth("ssd-header") {
                SectionHeader(
                    title = pluralStringResource(R.plurals.triage_confirm_ssd_title, plan.ssdOnly.size, plan.ssdOnly.size),
                    hint = null,
                ) {
                    NeoNotice(stringResource(R.string.triage_confirm_ssd_warning), tone = NeoTone.Coral)
                    if (!ssdAvailable) NeoNotice(stringResource(R.string.triage_confirm_plug_ssd))
                }
            }
            queueTiles(plan.ssdOnly, ssdAvailable, enabled, onOpenItem, onDequeue)
        }

        fullWidth("bottom-space") { Box(Modifier.padding(bottom = 16.dp)) }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = "header") { content() }
}

private fun LazyGridScope.queueTiles(
    items: List<GalleryItem>,
    ssdAvailable: Boolean,
    enabled: Boolean,
    onOpenItem: (String) -> Unit,
    onDequeue: (GalleryItem) -> Unit,
) {
    items(count = items.size, key = { items[it].key }, contentType = { "media" }) { index ->
        val item = items[index]
        Box {
            GalleryTile(
                item = item,
                onClick = { onOpenItem(item.key) },
                dimmed = !ssdAvailable,
            )
            // Retirer de la file : le média redevient proposé au tri.
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.triage_confirm_dequeue),
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(DequeueScrim, CircleShape)
                    .clickable(enabled = enabled) { onDequeue(item) }
                    .padding(4.dp)
                    .size(18.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, hint: String?, notices: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = NeoTheme.palette.content)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = NeoTheme.palette.content)
        notices()
    }
}

@Composable
private fun SsdDeletionDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        NeoCard(modifier = Modifier.padding(8.dp)) {
            Text(stringResource(R.string.gallery_delete_ssd_confirm_title), style = MaterialTheme.typography.titleLarge)
            NeoNotice(pluralStringResource(R.plurals.gallery_delete_ssd_confirm, count, count), tone = NeoTone.Coral)
            NeoButton(stringResource(R.string.gallery_delete_ssd_confirm_button), onClick = onConfirm, tone = NeoTone.Brick)
            NeoButton(stringResource(R.string.delete_confirm_cancel), onClick = onDismiss, tone = NeoTone.Surface)
        }
    }
}

private const val COLUMNS = 3
private val TILE_SPACING = 2.dp
private val DequeueScrim = Color.Black.copy(alpha = 0.55f)
