package dev.ybdn.ciaocloud.presentation.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.CropAspect
import dev.ybdn.ciaocloud.domain.model.EditUnavailableReason
import dev.ybdn.ciaocloud.presentation.components.NeoChipRow
import dev.ybdn.ciaocloud.presentation.components.NeoNotice
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.SaveEditOutcome
import dev.ybdn.ciaocloud.presentation.ciaoCloudSavedStateViewModel
import dev.ybdn.ciaocloud.presentation.components.NeoAction
import dev.ybdn.ciaocloud.presentation.components.NeoActionBar
import dev.ybdn.ciaocloud.presentation.components.NeoTopBar
import dev.ybdn.ciaocloud.presentation.gallery.GalleryImages
import dev.ybdn.ciaocloud.presentation.theme.NeoTheme
import kotlin.math.min

/**
 * Éditeur plein écran : barre du haut (Annuler, Enregistrer), aperçu au centre, outils en bas.
 * La géométrie est appliquée à l'aperçu par une transformation Compose.
 *
 * @param onSaved appelé après un enregistrement réussi (copie ou remplacement).
 */
@Composable
fun PhotoEditorScreen(
    item: GalleryItem,
    onClose: () -> Unit,
    onSaved: (SaveEditOutcome) -> Unit,
) {
    val viewModel = ciaoCloudSavedStateViewModel("editor:${item.key}") { container, app, handle ->
        PhotoEditorViewModel(container, app, handle, item)
    }
    val recipe by viewModel.recipe.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val ssdAvailable by viewModel.ssdAvailable.collectAsStateWithLifecycle()
    val transferRunning by viewModel.transferRunning.collectAsStateWithLifecycle()
    val capabilities = remember(item, ssdAvailable, transferRunning) { viewModel.capabilities() }
    var showSaveSheet by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Le ViewModel survit à la fermeture de l'éditeur (il appartient à la visionneuse) : chaque
    // nouvelle ouverture doit repartir d'une recette vierge.
    val close = {
        viewModel.reset()
        onClose()
    }
    val requestClose = { if (recipe.isIdentity) close() else showDiscardDialog = true }
    BackHandler(enabled = !isSaving, onBack = requestClose)

    LaunchedEffect(viewModel) {
        viewModel.outcomes.collect { outcome ->
            showSaveSheet = false
            when (outcome) {
                is SaveEditOutcome.Copied, SaveEditOutcome.Replaced -> {
                    viewModel.reset()
                    onSaved(outcome)
                }
                SaveEditOutcome.Cancelled, SaveEditOutcome.NothingToSave -> Unit
                SaveEditOutcome.SsdUnavailable -> context.toast(context.getString(R.string.editor_plug_ssd))
                is SaveEditOutcome.Unavailable -> context.toast(context.getString(outcome.reason.messageRes()))
                is SaveEditOutcome.Failed -> context.toast(context.getString(R.string.editor_save_failed, outcome.message))
            }
        }
    }

    if (showDiscardDialog) {
        DiscardChangesDialog(
            onDiscard = {
                showDiscardDialog = false
                close()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }
    if (showSaveSheet) {
        SaveEditDialog(
            item = item,
            capabilities = capabilities,
            onSave = viewModel::save,
            onDismiss = { showSaveSheet = false },
        )
    }
    if (isSaving) SavingDialog()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeoTheme.palette.page)
            // Plein écran au-dessus de la visionneuse : aucun geste ne doit l'atteindre.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        NeoTopBar(
            title = stringResource(R.string.editor_title),
            navigation = {
                IconButton(onClick = requestClose, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.editor_cancel), tint = NeoTheme.palette.content)
                }
            },
            actions = {
                IconButton(onClick = { showSaveSheet = true }, enabled = !recipe.isIdentity && !isSaving) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.editor_save))
                }
            },
        )

        var interacting by remember { mutableStateOf(false) }
        EditorPreview(
            item = item,
            viewModel = viewModel,
            showGrid = interacting,
            onInteraction = { interacting = it },
            modifier = Modifier.weight(1f),
        )

        val details by viewModel.details.collectAsStateWithLifecycle()
        if (details?.isMotionPhoto == true && !recipe.isOrientationOnly) {
            NeoNotice(
                stringResource(R.string.editor_motion_photo_still),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        val aspects = CropAspect.entries
        NeoChipRow(
            options = aspects.map { stringResource(it.labelRes()) },
            selectedIndex = aspects.indexOf(recipe.aspect),
            onSelect = { viewModel.selectAspect(aspects[it]) },
        )
        StraightenDial(
            degrees = recipe.straightenDegrees,
            onChange = viewModel::straighten,
            onDragStart = { interacting = true },
            onDragEnd = { interacting = false },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        NeoActionBar(
            actions = listOf(
                NeoAction(Icons.Outlined.RotateLeft, stringResource(R.string.editor_rotate), onClick = viewModel::rotateCounterClockwise),
                NeoAction(Icons.Outlined.Flip, stringResource(R.string.editor_mirror), onClick = viewModel::mirror),
                NeoAction(
                    Icons.Outlined.RestartAlt,
                    stringResource(R.string.editor_reset),
                    onClick = viewModel::reset,
                    enabled = !recipe.isIdentity,
                ),
            ),
        )
    }
}

/** Aperçu de l'image de travail (grand côté ≤ 2560 px) avec le cadre de recadrage. */
@Composable
private fun EditorPreview(
    item: GalleryItem,
    viewModel: PhotoEditorViewModel,
    showGrid: Boolean,
    onInteraction: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val recipe by viewModel.recipe.collectAsStateWithLifecycle()
    val originalUri by produceState<String?>(null, item.key) { value = viewModel.originalUri() }
    val uri = originalUri
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (uri == null) {
            CircularProgressIndicator()
            return@Box
        }
        val painter = rememberAsyncImagePainter(remember(uri) { GalleryImages.editorRequest(context, item, uri) })
        val state by painter.state.collectAsStateWithLifecycle()
        val intrinsic = (state as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize
        if (intrinsic == null || intrinsic.width <= 0f || intrinsic.height <= 0f) {
            CircularProgressIndicator()
            // Le painter doit être dessiné pour lancer le chargement.
            androidx.compose.foundation.Image(painter, contentDescription = null, modifier = Modifier.size(1.dp))
            return@Box
        }
        LaunchedEffect(intrinsic) { viewModel.onImageLoaded(intrinsic.width, intrinsic.height) }
        EditorCanvas(
            painter = painter,
            imageSize = intrinsic,
            recipe = recipe,
            cropMode = true,
            showGrid = showGrid,
            modifier = Modifier.fillMaxSize(),
            onGestureStart = { onInteraction(true) },
            onGestureEnd = { onInteraction(false) },
            onResize = viewModel::resizeCrop,
            onMove = viewModel::moveCrop,
            onZoom = viewModel::zoomCrop,
        )
    }
}

private fun CropAspect.labelRes(): Int = when (this) {
    CropAspect.FREE -> R.string.editor_aspect_free
    CropAspect.ORIGINAL -> R.string.editor_aspect_original
    CropAspect.SQUARE -> R.string.editor_aspect_square
    CropAspect.FOUR_THREE -> R.string.editor_aspect_4_3
    CropAspect.THREE_FOUR -> R.string.editor_aspect_3_4
    CropAspect.SIXTEEN_NINE -> R.string.editor_aspect_16_9
    CropAspect.NINE_SIXTEEN -> R.string.editor_aspect_9_16
    CropAspect.THREE_TWO -> R.string.editor_aspect_3_2
    CropAspect.TWO_THREE -> R.string.editor_aspect_2_3
}

fun EditUnavailableReason.messageRes(): Int = when (this) {
    EditUnavailableReason.SSD_REQUIRED -> R.string.editor_plug_ssd
    EditUnavailableReason.TRANSFER_RUNNING -> R.string.editor_transfer_running
    EditUnavailableReason.FORMAT_CHANGES_ON_SAVE -> R.string.editor_replace_format_changes
    EditUnavailableReason.METADATA_READ_ONLY -> R.string.editor_metadata_read_only
    else -> R.string.editor_unsupported
}

internal fun android.content.Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
