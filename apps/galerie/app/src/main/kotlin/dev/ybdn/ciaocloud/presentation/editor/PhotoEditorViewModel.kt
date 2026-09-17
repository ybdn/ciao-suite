package dev.ybdn.ciaocloud.presentation.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.ybdn.ciaocloud.di.AppContainer
import dev.ybdn.ciaocloud.domain.model.Adjustment
import dev.ybdn.ciaocloud.domain.model.Adjustments
import dev.ybdn.ciaocloud.domain.model.CropAspect
import dev.ybdn.ciaocloud.domain.model.EditCapabilities
import dev.ybdn.ciaocloud.domain.model.EditRecipe
import dev.ybdn.ciaocloud.domain.model.FilterPreset
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.ImageTransform
import dev.ybdn.ciaocloud.domain.model.MediaDetails
import dev.ybdn.ciaocloud.domain.model.NormalizedRect
import dev.ybdn.ciaocloud.domain.model.SaveEditOutcome
import dev.ybdn.ciaocloud.domain.model.SaveMode
import dev.ybdn.ciaocloud.domain.util.CropHandle
import dev.ybdn.ciaocloud.domain.util.EditHistory
import dev.ybdn.ciaocloud.domain.util.RecipeGeometry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

/** Recette affichée et disponibilité de l'historique. */
data class EditorState(
    val recipe: EditRecipe = EditRecipe(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

/**
 * État de l'éditeur : recette en cours (conservée dans le [SavedStateHandle] pour survivre à la mort
 * du processus), historique de 50 étapes et enregistrement. La géométrie est calculée sur les
 * dimensions réelles de la photo.
 */
class PhotoEditorViewModel(
    private val appContainer: AppContainer,
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    val item: GalleryItem,
) : AndroidViewModel(application) {

    private val history = EditHistory(RecipeState.restore(savedStateHandle))

    private val _state = MutableStateFlow(EditorState(history.current))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _outcomes = MutableSharedFlow<SaveEditOutcome>(extraBufferCapacity = 1)
    val outcomes: SharedFlow<SaveEditOutcome> = _outcomes.asSharedFlow()

    private val _details = MutableStateFlow<MediaDetails?>(null)
    val details: StateFlow<MediaDetails?> = _details.asStateFlow()

    val ssdAvailable: StateFlow<Boolean> = appContainer.observeSsdAvailabilityUseCase()

    val transferRunning: StateFlow<Boolean> = appContainer.getEditCapabilitiesUseCase.transferRunning

    /** Géométrie en pixels de l'original, connue une fois l'image de travail chargée. */
    private var geometry: RecipeGeometry? = null

    init {
        viewModelScope.launch { _details.value = appContainer.getMediaDetailsUseCase(item) }
    }

    fun capabilities(): EditCapabilities = appContainer.getEditCapabilitiesUseCase(item)

    suspend fun originalUri(): String? = appContainer.getOriginalUriUseCase(item)

    /**
     * Dimensions de l'image de travail (sous-échantillonnée, orientation appliquée) : ramenées à celles
     * de l'original quand MediaStore les connaît, pour la taille minimale du cadre.
     */
    fun onImageLoaded(width: Float, height: Float) {
        val realLongSide = item.phone?.let { max(it.width, it.height) }?.takeIf { it > 0 }?.toDouble()
        val scale = realLongSide?.let { it / max(width, height) } ?: 1.0
        geometry = RecipeGeometry(width * scale, height * scale)
    }

    // Actions ponctuelles : une étape d'historique chacune.

    fun rotateCounterClockwise() = applyGeometry { rotateCounterClockwise(it) }

    fun mirror() = applyGeometry { mirror(it) }

    fun selectAspect(aspect: CropAspect) = applyGeometry { aspect(it, aspect) }

    fun reset() = commit(EditRecipe())

    fun undo() = publish(history.undo())

    fun redo() = publish(history.redo())

    // Gestes continus (cadre, molette, curseurs) : une seule étape du début à la fin.

    fun beginGesture() = history.beginGesture()

    fun endGesture() {
        history.endGesture()
        publish(history.current)
    }

    fun straighten(degrees: Double) = updateGeometry { straighten(it, degrees) }

    fun resizeCrop(handle: CropHandle, dx: Double, dy: Double) = updateGeometry { resize(it, handle, dx, dy) }

    fun moveCrop(dx: Double, dy: Double) = updateGeometry { move(it, dx, dy) }

    fun zoomCrop(zoom: Double) = updateGeometry { zoom(it, zoom) }

    fun adjust(adjustment: Adjustment, value: Int) {
        history.update(history.current.copy(adjustments = history.current.adjustments.with(adjustment, value)))
        publish(history.current)
    }

    fun selectFilter(filter: FilterPreset) = commit(history.current.copy(filter = filter))

    fun setFilterIntensity(intensity: Int) {
        history.update(history.current.copy(filterIntensity = intensity.coerceIn(0, 100)))
        publish(history.current)
    }

    fun save(mode: SaveMode) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            try {
                _outcomes.emit(appContainer.savePhotoEditUseCase(item, history.current, mode, capabilities()))
            } finally {
                _isSaving.value = false
            }
        }
    }

    /** Nouvelle session d'édition (l'éditeur est rouvert) : recette et historique vierges. */
    fun clear() {
        history.reset()
        publish(history.current)
    }

    private fun applyGeometry(change: RecipeGeometry.(EditRecipe) -> EditRecipe) {
        val current = geometry ?: return
        commit(current.change(history.current))
    }

    private fun updateGeometry(change: RecipeGeometry.(EditRecipe) -> EditRecipe) {
        val current = geometry ?: return
        history.update(current.change(history.current))
        publish(history.current)
    }

    private fun commit(recipe: EditRecipe) {
        history.apply(recipe)
        publish(history.current)
    }

    private fun publish(recipe: EditRecipe) {
        _state.value = EditorState(recipe, history.canUndo, history.canRedo)
        RecipeState.save(savedStateHandle, recipe)
    }
}

/** Recette en types simples pour le [SavedStateHandle] (l'historique n'est pas conservé). */
private object RecipeState {
    private const val FLIPPED = "recipe_flipped"
    private const val ROTATION = "recipe_rotation"
    private const val STRAIGHTEN = "recipe_straighten"
    private const val CROP = "recipe_crop"
    private const val ASPECT = "recipe_aspect"
    private const val ADJUSTMENTS = "recipe_adjustments"
    private const val FILTER = "recipe_filter"
    private const val FILTER_INTENSITY = "recipe_filter_intensity"

    fun save(handle: SavedStateHandle, recipe: EditRecipe) {
        handle[FLIPPED] = recipe.transform.flipped
        handle[ROTATION] = recipe.transform.rotationDegrees
        handle[STRAIGHTEN] = recipe.straightenDegrees
        handle[CROP] = recipe.crop.let { doubleArrayOf(it.left, it.top, it.right, it.bottom) }
        handle[ASPECT] = recipe.aspect.name
        handle[ADJUSTMENTS] = recipe.adjustments.values()
        handle[FILTER] = recipe.filter.name
        handle[FILTER_INTENSITY] = recipe.filterIntensity
    }

    fun restore(handle: SavedStateHandle): EditRecipe {
        val crop = handle.get<DoubleArray>(CROP)?.takeIf { it.size == 4 }
        val adjustments = handle.get<IntArray>(ADJUSTMENTS)?.takeIf { it.size == Adjustment.entries.size }
        return EditRecipe(
            transform = ImageTransform(handle[FLIPPED] ?: false, handle[ROTATION] ?: 0),
            straightenDegrees = handle[STRAIGHTEN] ?: 0.0,
            crop = crop?.let { NormalizedRect(it[0], it[1], it[2], it[3]) } ?: NormalizedRect.FULL,
            aspect = handle.get<String>(ASPECT)?.let { name -> CropAspect.entries.firstOrNull { it.name == name } } ?: CropAspect.FREE,
            adjustments = adjustments?.let { runCatching { Adjustments.fromValues(it) }.getOrNull() } ?: Adjustments.NEUTRAL,
            filter = handle.get<String>(FILTER)?.let { name -> FilterPreset.entries.firstOrNull { it.name == name } } ?: FilterPreset.ORIGINAL,
            filterIntensity = handle[FILTER_INTENSITY] ?: 100,
        )
    }
}
