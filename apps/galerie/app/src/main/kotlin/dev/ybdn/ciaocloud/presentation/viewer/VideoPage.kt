package dev.ybdn.ciaocloud.presentation.viewer

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPresentationState
import coil3.compose.AsyncImage
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.presentation.gallery.GalleryImages
import dev.ybdn.ciaocloud.presentation.gallery.formatDuration
import dev.ybdn.ciaocloud.presentation.theme.Lime
import kotlinx.coroutines.delay

/**
 * Page vidéo : le lecteur n'existe que pour la page affichée. Changer de page le libère (donc
 * met la lecture en pause) ; quitter l'écran ou l'app met aussi en pause.
 */
@Composable
fun VideoPage(
    item: GalleryItem,
    originalUri: OriginalUri,
    isCurrentPage: Boolean,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggleChrome),
        contentAlignment = Alignment.Center,
    ) {
        if (isCurrentPage && originalUri is OriginalUri.Available) {
            VideoPlayer(item = item, uri = originalUri.uri, chromeVisible = chromeVisible)
        } else {
            AsyncImage(
                model = remember(item.key) { GalleryImages.thumbnailRequest(context, item) },
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (originalUri is OriginalUri.Unavailable) {
                SsdUnpluggedNotice()
            } else {
                Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(item: GalleryItem, uri: String, chromeVisible: Boolean) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val presentationState = rememberPresentationState(player)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Surface plein écran tant que la taille de la vidéo est inconnue : une surface de taille nulle
        // ne serait jamais créée, et la vidéo ne s'afficherait pas.
        val videoSize = presentationState.videoSizeDp
        PlayerSurface(
            player = player,
            modifier = if (videoSize == null || videoSize.width <= 0f || videoSize.height <= 0f) {
                Modifier.fillMaxSize()
            } else {
                Modifier.aspectRatio(videoSize.width / videoSize.height)
            },
        )
        // Vignette tant que la première image n'est pas rendue : pas de flash noir.
        if (presentationState.coverSurface) {
            AsyncImage(
                model = remember(item.key) { GalleryImages.thumbnailRequest(context, item) },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ViewerBottomBarContainer {
                VideoControls(player)
            }
        }
    }
}

@Composable
private fun VideoControls(player: Player) {
    val playPauseState = rememberPlayPauseButtonState(player)
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var seekingFraction by remember { mutableStateOf<Float?>(null) }
    var muted by remember { mutableStateOf(player.volume == 0f) }

    LaunchedEffect(player) {
        while (true) {
            durationMs = player.duration.coerceAtLeast(0L)
            positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(POSITION_POLL_MS)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = ACTION_BAR_RESERVED_HEIGHT),
    ) {
        IconButton(onClick = playPauseState::onClick, enabled = playPauseState.isEnabled) {
            Icon(
                imageVector = if (playPauseState.showPlay) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = stringResource(if (playPauseState.showPlay) R.string.viewer_play else R.string.viewer_pause),
                tint = Color.White,
            )
        }
        val fraction = seekingFraction
            ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        Text(
            text = formatDuration(if (seekingFraction != null) (fraction * durationMs).toLong() else positionMs),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
        )
        Slider(
            value = fraction,
            onValueChange = { seekingFraction = it },
            onValueChangeFinished = {
                seekingFraction?.let { player.seekTo((it * durationMs).toLong()) }
                seekingFraction = null
            },
            enabled = durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = Lime,
                activeTrackColor = Lime,
                inactiveTrackColor = Color.White.copy(alpha = 0.35f),
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(text = formatDuration(durationMs), style = MaterialTheme.typography.bodySmall, color = Color.White)
        IconButton(onClick = {
            muted = !muted
            player.volume = if (muted) 0f else 1f
        }) {
            Icon(
                imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(if (muted) R.string.viewer_unmute else R.string.viewer_mute),
                tint = Color.White,
            )
        }
    }
}

private const val POSITION_POLL_MS = 250L

/** Place laissée sous les contrôles vidéo pour la barre d'actions de la visionneuse. */
val ACTION_BAR_RESERVED_HEIGHT = 0.dp
