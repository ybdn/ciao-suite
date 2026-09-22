package dev.ybdn.ciaocloud.presentation.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciao.designsystem.components.stableNavigationBarsPadding
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.presentation.ciaoCloudViewModel
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoTone
import dev.ybdn.ciao.designsystem.components.NeoTopBar
import dev.ybdn.ciaocloud.presentation.gallery.GalleryTile
import dev.ybdn.ciao.designsystem.theme.NeoTheme
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Corbeille système : restaurer ou supprimer définitivement les médias supprimés du téléphone. */
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val viewModel = ciaoCloudViewModel { container, app -> TrashViewModel(container, app) }
    val trashed by viewModel.trashed.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        NeoTopBar(
            title = stringResource(R.string.trash_title),
            navigation = {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.viewer_back))
                }
            },
        )
        Text(
            stringResource(R.string.trash_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = NeoTheme.palette.content,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )

        val items = trashed
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                items == null -> CircularProgressIndicator()
                items.isEmpty() -> Text(
                    stringResource(R.string.trash_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeoTheme.palette.content,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.media.mediaStoreId }) { trashedMedia ->
                        val media = trashedMedia.media
                        val id = media.mediaStoreId
                        Box {
                            GalleryTile(
                                item = GalleryItem(
                                    key = "trash:$id",
                                    phone = media,
                                    ssd = null,
                                    captureDate = LocalDate.MIN,
                                    sortEpochMillis = null,
                                ),
                                onClick = { viewModel.toggle(id) },
                                selected = id in selectedIds,
                                showBackupBadge = false,
                            )
                            val daysLeft = TimeUnit.MILLISECONDS
                                .toDays(trashedMedia.expiresAtEpochMillis - System.currentTimeMillis())
                                .coerceAtLeast(0)
                            Text(
                                stringResource(R.string.trash_expires_days, daysLeft),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                            )
                        }
                    }
                }
            }
        }

        if (!items.isNullOrEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp).stableNavigationBarsPadding(),
            ) {
                NeoButton(stringResource(R.string.trash_select_all), onClick = viewModel::selectAll, tone = NeoTone.Surface)
                NeoButton(
                    stringResource(R.string.trash_restore),
                    onClick = { viewModel.restoreSelection() },
                    tone = NeoTone.Green,
                    enabled = selectedIds.isNotEmpty(),
                )
                NeoButton(
                    stringResource(R.string.trash_delete_forever),
                    onClick = { viewModel.deleteSelectionForever() },
                    tone = NeoTone.Pink,
                    enabled = selectedIds.isNotEmpty(),
                )
            }
        }
    }
}
