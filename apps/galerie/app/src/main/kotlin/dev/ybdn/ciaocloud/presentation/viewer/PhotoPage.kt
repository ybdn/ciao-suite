package dev.ybdn.ciaocloud.presentation.viewer

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.presentation.gallery.GalleryImages
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState

/**
 * Photo zoomable (pincement, double-appui). Telephoto décode les grandes photos par tuiles
 * sous-échantillonnées : une photo de 50 Mpx n'est jamais chargée entière en mémoire.
 */
@Composable
fun ZoomablePhotoPage(
    item: GalleryItem,
    originalUri: OriginalUri,
    isCurrentPage: Boolean,
    onToggleChrome: () -> Unit,
) {
    val context = LocalContext.current
    val request = remember(item.key, originalUri) {
        when (originalUri) {
            is OriginalUri.Available -> GalleryImages.originalRequest(context, item, originalUri.uri)
            // En attendant l'URI, ou SSD débranché : vignette agrandie.
            else -> GalleryImages.thumbnailRequest(context, item)
        }
    }
    val zoomState = rememberZoomableImageState()

    // Une page quittée revient à l'échelle 1 : on ne retrouve pas une photo zoomée en y revenant.
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) zoomState.zoomableState.resetZoom(animationSpec = snap())
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ZoomableAsyncImage(
            model = request,
            contentDescription = item.displayName,
            state = zoomState,
            onClick = { onToggleChrome() },
            modifier = Modifier.fillMaxSize(),
        )
        if (originalUri is OriginalUri.Unavailable) {
            SsdUnpluggedNotice(Modifier.align(Alignment.Center))
        }
    }
}
