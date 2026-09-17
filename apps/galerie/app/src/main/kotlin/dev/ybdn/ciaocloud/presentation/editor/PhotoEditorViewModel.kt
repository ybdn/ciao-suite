package dev.ybdn.ciaocloud.presentation.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.ybdn.ciaocloud.di.AppContainer
import dev.ybdn.ciaocloud.domain.model.CropAspect
import dev.ybdn.ciaocloud.domain.model.EditCapabilities
import dev.ybdn.ciaocloud.domain.model.EditRecipe
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.ImageTransform
import dev.ybdn.ciaocloud.domain.model.MediaDetails
import dev.ybdn.ciaocloud.domain.model.NormalizedRect
import dev.ybdn.ciaocloud.domain.model.SaveEditOutcome
import dev.ybdn.ciaocloud.domain.model.SaveMode
import dev.ybdn.ciaocloud.domain.util.CropHandle
import dev.ybdn.ciaocloud.domain.util.RecipeGeometry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * État de l'éditeur : recette en cours (conservée dans le [SavedStateHandle] pour survivre à la mort
 * du processus) et enregistrement. La géométrie est calculée sur les dimensions réelles de la photo.
 */
class PhotoEditorViewModel(
    private val appContainer: AppContainer,
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    val item: GalleryItem,
) : AndroidViewModel(application) {

    private val _recipe = MutableStateFlow(RecipeState.restore(savedStateHandle))
    val recipe: StateFlow<EditRecipe> = _recipe.asStateFlow()

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
        val phone = item.phone
        val realLongSide = phone?.let { max(it.width, it.height) }?.takeIf { it > 0 }?.toDouble()
        val scale = realLongSide?.let { it / max(width, height) } ?: 1.0
        geometry = RecipeGeometry(width * scale, height * scale)
    }

    fun rotateCounterClockwise() = updateGeometry { rotateCounterClockwise(it) }

    fun mirror() = updateGeometry { mirror(it) }

    fun straighten(degrees: Double) = updateGeometry { straighten(it, degrees) }

    fun selectAspect(aspect: CropAspect) = updateGeometry { aspect(it, aspect) }

    fun resizeCrop(handle: CropHandle, dx: Double, dy: Double) = updateGeometry { resize(it, handle, dx, dy) }

    fun moveCrop(dx: Double, dy: Double) = updateGeometry { move(it, dx, dy) }

    fun zoomCrop(zoom: Double) = updateGeometry { zoom(it, zoom) }

    fun reset() = updateRecipe(EditRecipe())

    fun save(mode: SaveMode) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            try {
                _outcomes.emit(appContainer.savePhotoEditUseCase(item, _recipe.value, mode, capabilities()))
            } finally {
                _isSaving.value = false
            }
        }
    }

    private fun updateGeometry(change: RecipeGeometry.(EditRecipe) -> EditRecipe) {
        val current = geometry ?: return
        updateRecipe(current.change(_recipe.value))
    }

    private fun updateRecipe(recipe: EditRecipe) {
        _recipe.value = recipe
        RecipeState.save(savedStateHandle, recipe)
    }
}

/** Recette en types simples pour le [SavedStateHandle]. */
private object RecipeState {
    private const val FLIPPED = "recipe_flipped"
    private const val ROTATION = "recipe_rotation"
    private const val STRAIGHTEN = "recipe_straighten"
    private const val CROP = "recipe_crop"
    private const val ASPECT = "recipe_aspect"

    fun save(handle: SavedStateHandle, recipe: EditRecipe) {
        handle[FLIPPED] = recipe.transform.flipped
        handle[ROTATION] = recipe.transform.rotationDegrees
        handle[STRAIGHTEN] = recipe.straightenDegrees
        handle[CROP] = recipe.crop.let { doubleArrayOf(it.left, it.top, it.right, it.bottom) }
        handle[ASPECT] = recipe.aspect.name
    }

    fun restore(handle: SavedStateHandle): EditRecipe {
        val crop = handle.get<DoubleArray>(CROP)?.takeIf { it.size == 4 }
        return EditRecipe(
            transform = ImageTransform(handle[FLIPPED] ?: false, handle[ROTATION] ?: 0),
            straightenDegrees = handle[STRAIGHTEN] ?: 0.0,
            crop = crop?.let { NormalizedRect(it[0], it[1], it[2], it[3]) } ?: NormalizedRect.FULL,
            aspect = handle.get<String>(ASPECT)?.let { name -> CropAspect.entries.firstOrNull { it.name == name } } ?: CropAspect.FREE,
        )
    }
}
