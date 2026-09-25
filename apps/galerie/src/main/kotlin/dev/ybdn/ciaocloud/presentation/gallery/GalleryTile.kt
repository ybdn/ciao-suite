package dev.ybdn.ciaocloud.presentation.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.GalleryLocation
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciao.designsystem.theme.Ink
import dev.ybdn.ciao.designsystem.theme.Selected
import dev.ybdn.ciao.designsystem.theme.NeoTheme

@OptIn(ExperimentalFoundationApi::class)
/** Vignette carrée sans bordure (lisibilité et performance), badges discrets sur voile sombre. */
@Composable
fun GalleryTile(
    item: GalleryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    dimmed: Boolean = false,
    selected: Boolean? = null,
    showBackupBadge: Boolean = true,
) {
    val context = LocalContext.current
    val request = remember(item.key, item.phone?.dateModifiedEpochMillis, item.ssd?.lastModifiedEpochMillis) { GalleryImages.thumbnailRequest(context, item) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(NeoTheme.palette.surfaceMuted)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = request,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (selected == true) Modifier.padding(SELECTED_INSET) else Modifier)
                .alpha(if (dimmed) DIMMED_ALPHA else 1f),
        )

        if (showBackupBadge) when (item.location) {
            GalleryLocation.PHONE -> TileBadgeIcon(Icons.Outlined.CloudOff, Modifier.align(Alignment.TopEnd))
            GalleryLocation.SSD -> TileBadgeIcon(Icons.Outlined.SdStorage, Modifier.align(Alignment.TopEnd))
            GalleryLocation.BOTH -> Unit
        }

        if (item.isFavorite) {
            TileBadgeIcon(Icons.Filled.Favorite, Modifier.align(Alignment.BottomStart))
        }

        // Mode sélection : case à cocher en haut à gauche de chaque vignette.
        if (selected != null) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (selected) Ink else Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(if (selected) Selected else BadgeScrim, CircleShape)
                    .size(22.dp),
            )
        }

        if (item.mediaType == MediaType.VIDEO) {
            Text(
                text = item.phone?.durationMillis?.let(::formatDuration) ?: "▶",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(BadgeScrim, RectangleShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun TileBadgeIcon(icon: ImageVector, modifier: Modifier) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
            .padding(4.dp)
            .background(BadgeScrim, CircleShape)
            .padding(3.dp)
            .size(13.dp),
    )
}

private val BadgeScrim = Color.Black.copy(alpha = 0.45f)
private const val DIMMED_ALPHA = 0.55f
private val SELECTED_INSET = 10.dp
