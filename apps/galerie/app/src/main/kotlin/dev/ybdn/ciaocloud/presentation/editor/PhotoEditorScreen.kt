package dev.ybdn.ciaocloud.presentation.editor

import android.graphics.RenderEffect
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.data.edit.MonoMix
import dev.ybdn.ciaocloud.data.edit.PhotoAdjustmentShader
import dev.ybdn.ciaocloud.domain.model.Adjustment
import dev.ybdn.ciaocloud.domain.model.CropAspect
import dev.ybdn.ciaocloud.domain.model.EditRecipe
import dev.ybdn.ciaocloud.domain.model.EditUnavailableReason
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.SaveEditOutcome
import dev.ybdn.ciaocloud.presentation.ciaoCloudSavedStateViewModel
import dev.ybdn.ciaocloud.presentation.components.NeoAction
import dev.ybdn.ciaocloud.presentation.components.NeoActionBar
import dev.ybdn.ciaocloud.presentation.components.NeoChipRow
import dev.ybdn.ciaocloud.presentation.components.NeoNotice
import dev.ybdn.ciaocloud.presentation.components.NeoTopBar
import dev.ybdn.ciaocloud.presentation.gallery.GalleryImages
import dev.ybdn.ciaocloud.presentation.theme.NeoTheme

/** Onglets d'outils de l'éditeur. */
enum class EditorTab { CROP, LIGHT, COLOR, EFFECTS }

/**
 * Éditeur plein écran : barre du haut (Annuler, historique, Enregistrer), aperçu au centre, outils
 * de l'onglet courant en bas. Appui long sur l'aperçu : original affiché tant que le doigt reste posé.
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recipe = state.recipe
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val ssdAvailable by viewModel.ssdAvailable.collectAsStateWithLifecycle()
    val transferRunning by viewModel.transferRunning.collectAsStateWithLifecycle()
    val capabilities = remember(item, ssdAvailable, transferRunning) { viewModel.capabilities() }
    var tab by rememberSaveable { mutableStateOf(EditorTab.CROP) }
    var showSaveSheet by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var interacting by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Le ViewModel survit à la fermeture de l'éditeur (il appartient à la visionneuse) : chaque
    // nouvelle ouverture doit repartir d'une recette vierge.
    val close = {
        viewModel.clear()
        onClose()
    }
    val requestClose = { if (recipe.isIdentity) close() else showDiscardDialog = true }
    BackHandler(enabled = !isSaving, onBack = requestClose)

    LaunchedEffect(viewModel) {
        viewModel.outcomes.collect { outcome ->
            showSaveSheet = false
            when (outcome) {
                is SaveEditOutcome.Copied, SaveEditOutcome.Replaced -> {
                    viewModel.clear()
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
        SaveEditDialog(item = item, capabilities = capabilities, onSave = viewModel::save, onDismiss = { showSaveSheet = false })
    }
    if (isSaving) SavingDialog()

    val beginGesture = {
        interacting = true
        viewModel.beginGesture()
    }
    val endGesture = {
        interacting = false
        viewModel.endGesture()
    }

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
                IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = stringResource(R.string.editor_undo))
                }
                IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                    Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = stringResource(R.string.editor_redo))
                }
                IconButton(onClick = viewModel::reset, enabled = !recipe.isIdentity) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.editor_reset_all))
                }
                IconButton(onClick = { showSaveSheet = true }, enabled = !recipe.isIdentity && !isSaving) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.editor_save))
                }
            },
        )

        EditorPreview(
            item = item,
            viewModel = viewModel,
            recipe = recipe,
            cropMode = tab == EditorTab.CROP,
            showGrid = interacting,
            onGestureStart = beginGesture,
            onGestureEnd = endGesture,
            modifier = Modifier.weight(1f),
        )

        val details by viewModel.details.collectAsStateWithLifecycle()
        if (details?.isMotionPhoto == true && !recipe.isOrientationOnly) {
            NeoNotice(
                stringResource(R.string.editor_motion_photo_still),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        when (tab) {
            EditorTab.CROP -> CropTools(recipe, viewModel, beginGesture, endGesture)
            EditorTab.LIGHT -> AdjustmentTools(
                listOf(
                    Adjustment.BRIGHTNESS, Adjustment.CONTRAST, Adjustment.HIGHLIGHTS,
                    Adjustment.SHADOWS, Adjustment.WHITES, Adjustment.BLACKS,
                ),
                recipe, viewModel, beginGesture, endGesture,
            )
            EditorTab.COLOR -> AdjustmentTools(
                listOf(Adjustment.SATURATION, Adjustment.VIBRANCE, Adjustment.TEMPERATURE, Adjustment.TINT),
                recipe, viewModel, beginGesture, endGesture,
            )
            EditorTab.EFFECTS -> AdjustmentTools(
                listOf(Adjustment.SHARPNESS, Adjustment.VIGNETTE),
                recipe, viewModel, beginGesture, endGesture,
            )
        }

        val tabs = EditorTab.entries
        NeoChipRow(
            options = tabs.map { stringResource(it.labelRes()) },
            selectedIndex = tabs.indexOf(tab),
            onSelect = { tab = tabs[it] },
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (tab == EditorTab.CROP) {
            NeoActionBar(
                actions = listOf(
                    NeoAction(Icons.Outlined.RotateLeft, stringResource(R.string.editor_rotate), onClick = viewModel::rotateCounterClockwise),
                    NeoAction(Icons.Outlined.Flip, stringResource(R.string.editor_mirror), onClick = viewModel::mirror),
                ),
            )
        } else {
            NeoActionBar(actions = emptyList())
        }
    }
}

@Composable
private fun CropTools(
    recipe: EditRecipe,
    viewModel: PhotoEditorViewModel,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
) {
    val aspects = CropAspect.entries
    NeoChipRow(
        options = aspects.map { stringResource(it.labelRes()) },
        selectedIndex = aspects.indexOf(recipe.aspect),
        onSelect = { viewModel.selectAspect(aspects[it]) },
    )
    StraightenDial(
        degrees = recipe.straightenDegrees,
        onChange = viewModel::straighten,
        onDragStart = onGestureStart,
        onDragEnd = onGestureEnd,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun AdjustmentTools(
    adjustments: List<Adjustment>,
    recipe: EditRecipe,
    viewModel: PhotoEditorViewModel,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 250.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        adjustments.forEach { adjustment ->
            AdjustmentSlider(
                label = stringResource(adjustment.labelRes()),
                value = recipe.adjustments[adjustment],
                min = adjustment.min,
                max = 100,
                onChange = { viewModel.adjust(adjustment, it) },
                onGestureStart = onGestureStart,
                onGestureEnd = onGestureEnd,
            )
        }
    }
}

/** Aperçu de l'image de travail (grand côté ≤ 2560 px) : cadre en recadrage, résultat réglé sinon. */
@Composable
private fun EditorPreview(
    item: GalleryItem,
    viewModel: PhotoEditorViewModel,
    recipe: EditRecipe,
    cropMode: Boolean,
    showGrid: Boolean,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shader = remember { PhotoAdjustmentShader(context.applicationContext) }
    val originalUri by produceState<String?>(null, item.key) { value = viewModel.originalUri() }
    var showOriginal by remember { mutableStateOf(false) }
    val uri = originalUri
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (uri == null) {
            CircularProgressIndicator()
            return@Box
        }
        val painter = rememberAsyncImagePainter(remember(uri) { GalleryImages.editorRequest(context, item, uri) })
        val painterState by painter.state.collectAsStateWithLifecycle()
        val intrinsic = (painterState as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize
        if (intrinsic == null || intrinsic.width <= 0f || intrinsic.height <= 0f) {
            CircularProgressIndicator()
            // Le painter doit être dessiné pour lancer le chargement.
            Image(painter, contentDescription = null, modifier = Modifier.size(1.dp))
            return@Box
        }
        LaunchedEffect(intrinsic) { viewModel.onImageLoaded(intrinsic.width, intrinsic.height) }
        val displayed = if (showOriginal) EditRecipe() else recipe
        val adjustments = displayed.adjustments
        EditorCanvas(
            painter = painter,
            imageSize = intrinsic,
            recipe = displayed,
            cropMode = cropMode && !showOriginal,
            showGrid = showGrid,
            shaderEffect = if (adjustments.isNeutral) {
                null
            } else {
                { frame ->
                    RenderEffect.createRuntimeShaderEffect(
                        shader.create(adjustments, MonoMix.NONE, frame.left, frame.top, frame.width, frame.height),
                        "image",
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (cropMode) {
                        Modifier
                    } else {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { showOriginal = true },
                                onPress = {
                                    tryAwaitRelease()
                                    showOriginal = false
                                },
                            )
                        }
                    },
                ),
            onGestureStart = onGestureStart,
            onGestureEnd = onGestureEnd,
            onResize = viewModel::resizeCrop,
            onMove = viewModel::moveCrop,
            onZoom = viewModel::zoomCrop,
        )
    }
}

private fun EditorTab.labelRes(): Int = when (this) {
    EditorTab.CROP -> R.string.editor_tab_crop
    EditorTab.LIGHT -> R.string.editor_tab_light
    EditorTab.COLOR -> R.string.editor_tab_color
    EditorTab.EFFECTS -> R.string.editor_tab_effects
}

private fun Adjustment.labelRes(): Int = when (this) {
    Adjustment.BRIGHTNESS -> R.string.editor_brightness
    Adjustment.CONTRAST -> R.string.editor_contrast
    Adjustment.HIGHLIGHTS -> R.string.editor_highlights
    Adjustment.SHADOWS -> R.string.editor_shadows
    Adjustment.WHITES -> R.string.editor_whites
    Adjustment.BLACKS -> R.string.editor_blacks
    Adjustment.SATURATION -> R.string.editor_saturation
    Adjustment.VIBRANCE -> R.string.editor_vibrance
    Adjustment.TEMPERATURE -> R.string.editor_temperature
    Adjustment.TINT -> R.string.editor_tint
    Adjustment.SHARPNESS -> R.string.editor_sharpness
    Adjustment.VIGNETTE -> R.string.editor_vignette
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
