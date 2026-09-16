package dev.ybdn.ciaocloud.presentation.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.presentation.gallery.GalleryImages

/** Page vidéo : aperçu en attendant le lecteur intégré. */
@Composable
fun VideoPage(
    item: GalleryItem,
    @Suppress("UNUSED_PARAMETER") isCurrentPage: Boolean,
    @Suppress("UNUSED_PARAMETER") chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggleChrome),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = remember(item.key) { GalleryImages.thumbnailRequest(context, item) },
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
    }
}
