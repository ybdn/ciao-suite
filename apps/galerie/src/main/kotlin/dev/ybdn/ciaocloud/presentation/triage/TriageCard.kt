package dev.ybdn.ciaocloud.presentation.triage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.GalleryLocation
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.model.TriageDecision
import dev.ybdn.ciao.designsystem.components.BorderWidth
import dev.ybdn.ciao.designsystem.components.NeoTag
import dev.ybdn.ciao.designsystem.components.NeoTone
import dev.ybdn.ciao.designsystem.components.neoSurface
import dev.ybdn.ciaocloud.presentation.gallery.GalleryImages
import dev.ybdn.ciaocloud.presentation.gallery.formatDuration
import dev.ybdn.ciao.designsystem.theme.Pink
import dev.ybdn.ciao.designsystem.theme.Ink
import dev.ybdn.ciao.designsystem.theme.Green
import dev.ybdn.ciao.designsystem.theme.NeoTheme
import dev.ybdn.ciao.designsystem.theme.Yellow
import dev.ybdn.ciaocloud.presentation.viewer.OriginalUri
import dev.ybdn.ciaocloud.presentation.viewer.SsdUnpluggedNotice
import dev.ybdn.ciaocloud.presentation.viewer.VideoPage
import dev.ybdn.ciaocloud.presentation.viewer.rememberOriginalUri
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * Position de la carte pendant le geste. Mappage des directions (indicatif, cf. points ouverts de
 * la spec) : gauche = supprimer, droite = garder, haut = revoir plus tard.
 */
@Stable
class SwipeCardState {
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    var size by mutableStateOf(IntSize.Zero)

    /** Une décision est en cours d'animation : gestes et boutons sont ignorés. */
    var isSettling by mutableStateOf(false)
        private set

    /** Décision vers laquelle pointe le déplacement [offset], et sa progression (1 = seuil atteint). */
    fun intent(offset: Offset = this.offset.value): Pair<TriageDecision, Float>? {
        if (size.width == 0 || size.height == 0) return null
        val horizontal = offset.x / (size.width * HORIZONTAL_THRESHOLD)
        val up = -offset.y / (size.height * UP_THRESHOLD)
        return when {
            up > 0f && up > abs(horizontal) -> TriageDecision.SNOOZED to up
            horizontal > 0f -> TriageDecision.KEPT to horizontal
            horizontal < 0f -> TriageDecision.QUEUED_FOR_DELETION to -horizontal
            else -> null
        }
    }

    suspend fun dragBy(amount: Offset) {
        if (!isSettling) offset.snapTo(offset.value + amount)
    }

    /** Fin du geste : décision si le seuil est franchi, sinon retour au centre. */
    suspend fun settle(onDecide: (TriageDecision) -> Unit) {
        val (decision, progress) = intent() ?: (null to 0f)
        if (decision != null && progress >= 1f) {
            fling(decision, onDecide)
        } else {
            offset.animateTo(Offset.Zero, spring())
        }
    }

    /** Envoie la carte hors de l'écran dans la direction de [decision], puis la tranche. */
    suspend fun fling(decision: TriageDecision, onDecide: (TriageDecision) -> Unit) {
        if (isSettling) return
        isSettling = true
        val target = when (decision) {
            TriageDecision.KEPT -> Offset(size.width * FLING_DISTANCE, offset.value.y)
            TriageDecision.QUEUED_FOR_DELETION -> Offset(-size.width * FLING_DISTANCE, offset.value.y)
            TriageDecision.SNOOZED -> Offset(offset.value.x, -size.height * FLING_DISTANCE)
        }
        offset.animateTo(target, tween(durationMillis = FLING_DURATION_MS))
        onDecide(decision)
    }

    private companion object {
        const val HORIZONTAL_THRESHOLD = 0.3f
        const val UP_THRESHOLD = 0.2f
        const val FLING_DISTANCE = 1.5f
        const val FLING_DURATION_MS = 200
    }
}

/**
 * Carte de tri : le média en plein cadre sur la surface de l'app (pas de fond noir), badge de
 * sauvegarde, tampon de la décision visé pendant le geste. [state] null : carte suivante, inerte.
 */
@Composable
fun TriageCard(
    item: GalleryItem,
    state: SwipeCardState?,
    ssdAvailable: Boolean,
    resolveOriginal: suspend (GalleryItem) -> String?,
    onDecide: (TriageDecision) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val palette = NeoTheme.palette

    Box(
        modifier = modifier
            .then(
                if (state == null) {
                    Modifier
                } else {
                    Modifier
                        .onSizeChanged { state.size = it }
                        .graphicsLayer {
                            translationX = state.offset.value.x
                            translationY = state.offset.value.y
                            rotationZ = if (size.width > 0f) state.offset.value.x / size.width * MAX_ROTATION_DEGREES else 0f
                        }
                        .pointerInput(item.key) {
                            detectDragGestures(
                                onDragEnd = { scope.launch { state.settle(onDecide) } },
                                onDragCancel = { scope.launch { state.offset.animateTo(Offset.Zero, spring()) } },
                            ) { change, amount ->
                                change.consume()
                                scope.launch { state.dragBy(amount) }
                            }
                        }
                },
            )
            .neoSurface(palette.surfaceMuted, palette.outline)
            .padding(BorderWidth)
            .clip(RectangleShape),
    ) {
        val originalUri = rememberOriginalUri(item, ssdAvailable, resolveOriginal)
        when (item.mediaType) {
            MediaType.PHOTO -> PhotoContent(item, originalUri)
            MediaType.VIDEO -> VideoContent(item, originalUri, interactive = state != null)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        ) {
            BackupTag(item.location)
            if (item.mediaType == MediaType.VIDEO) {
                NeoTag(item.phone?.durationMillis?.let(::formatDuration) ?: stringResource(R.string.triage_video), tone = NeoTone.Surface)
            }
        }

        if (state != null) DecisionStampOverlay(state)
    }
}

@Composable
private fun PhotoContent(item: GalleryItem, originalUri: OriginalUri) {
    val context = LocalContext.current
    val request = remember(item.key, originalUri, item.phone?.dateModifiedEpochMillis, item.ssd?.lastModifiedEpochMillis) {
        when (originalUri) {
            is OriginalUri.Available -> GalleryImages.originalRequest(context, item, originalUri.uri)
            // En attendant l'URI, ou SSD débranché : vignette agrandie.
            else -> GalleryImages.thumbnailRequest(context, item)
        }
    }
    AsyncImage(
        model = request,
        contentDescription = item.displayName,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
    )
}

/** Vignette statique ; un appui lance la lecture dans la carte. Le lecteur disparaît avec la carte. */
@Composable
private fun VideoContent(item: GalleryItem, originalUri: OriginalUri, interactive: Boolean) {
    val context = LocalContext.current
    var playing by remember(item.key) { mutableStateOf(false) }
    var controlsVisible by remember(item.key) { mutableStateOf(true) }

    if (playing && originalUri is OriginalUri.Available) {
        VideoPage(
            item = item,
            originalUri = originalUri,
            isCurrentPage = true,
            chromeVisible = controlsVisible,
            bottomInset = 0.dp,
            onToggleChrome = { controlsVisible = !controlsVisible },
        )
        return
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = interactive && originalUri is OriginalUri.Available) { playing = true },
    ) {
        AsyncImage(
            model = remember(item.key) { GalleryImages.thumbnailRequest(context, item) },
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (originalUri is OriginalUri.Unavailable) {
            SsdUnpluggedNotice()
        } else {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.viewer_play),
                tint = Ink,
                modifier = Modifier
                    .neoSurface(Green, NeoTheme.palette.outline)
                    .padding(12.dp)
                    .size(40.dp),
            )
        }
    }
}

@Composable
private fun BackupTag(location: GalleryLocation) {
    when (location) {
        GalleryLocation.PHONE -> NeoTag(stringResource(R.string.triage_badge_not_backed_up), tone = NeoTone.Yellow)
        GalleryLocation.BOTH -> NeoTag(stringResource(R.string.triage_badge_backed_up), tone = NeoTone.Green)
        GalleryLocation.SSD -> NeoTag(stringResource(R.string.triage_badge_ssd), tone = NeoTone.Sky)
    }
}

/** Tampon de la décision visée ; seul ce bloc se recompose à chaque image du geste. */
@Composable
private fun BoxScope.DecisionStampOverlay(state: SwipeCardState) {
    val (decision, progress) = state.intent() ?: return
    DecisionStamp(
        decision = decision,
        alpha = min(1f, progress),
        modifier = Modifier.align(
            when (decision) {
                TriageDecision.KEPT -> Alignment.CenterStart
                TriageDecision.QUEUED_FOR_DELETION -> Alignment.CenterEnd
                TriageDecision.SNOOZED -> Alignment.BottomCenter
            },
        ),
    )
}

@Composable
private fun DecisionStamp(decision: TriageDecision, alpha: Float, modifier: Modifier = Modifier) {
    val (label, color) = when (decision) {
        TriageDecision.KEPT -> R.string.triage_keep to Green
        TriageDecision.QUEUED_FOR_DELETION -> R.string.triage_delete to Pink
        TriageDecision.SNOOZED -> R.string.triage_later to Yellow
    }
    Text(
        text = stringResource(label).uppercase(),
        style = MaterialTheme.typography.headlineSmall,
        color = Ink,
        modifier = modifier
            .padding(24.dp)
            .graphicsLayer {
                this.alpha = alpha
                rotationZ = when (decision) {
                    TriageDecision.KEPT -> -STAMP_ROTATION_DEGREES
                    TriageDecision.QUEUED_FOR_DELETION -> STAMP_ROTATION_DEGREES
                    TriageDecision.SNOOZED -> 0f
                }
            }
            .neoSurface(color, Ink)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

private const val MAX_ROTATION_DEGREES = 12f
private const val STAMP_ROTATION_DEGREES = 10f
