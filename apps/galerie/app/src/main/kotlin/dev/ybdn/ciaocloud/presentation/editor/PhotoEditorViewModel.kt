package dev.ybdn.ciaocloud.presentation.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.ybdn.ciaocloud.di.AppContainer
import dev.ybdn.ciaocloud.domain.model.EditCapabilities
import dev.ybdn.ciaocloud.domain.model.EditRecipe
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.ImageTransform
import dev.ybdn.ciaocloud.domain.model.SaveEditOutcome
import dev.ybdn.ciaocloud.domain.model.SaveMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * État de l'éditeur : recette en cours (conservée dans le [SavedStateHandle] pour survivre à la mort
 * du processus) et enregistrement.
 */
class PhotoEditorViewModel(
    private val appContainer: AppContainer,
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    val item: GalleryItem,
) : AndroidViewModel(application) {

    private val _recipe = MutableStateFlow(restoreRecipe())
    val recipe: StateFlow<EditRecipe> = _recipe.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _outcomes = MutableSharedFlow<SaveEditOutcome>(extraBufferCapacity = 1)
    val outcomes: SharedFlow<SaveEditOutcome> = _outcomes.asSharedFlow()

    val ssdAvailable: StateFlow<Boolean> = appContainer.observeSsdAvailabilityUseCase()

    val transferRunning: StateFlow<Boolean> = appContainer.getEditCapabilitiesUseCase.transferRunning

    fun capabilities(): EditCapabilities = appContainer.getEditCapabilitiesUseCase(item)

    suspend fun originalUri(): String? = appContainer.getOriginalUriUseCase(item)

    fun rotateCounterClockwise() = updateTransform { it.rotatedCounterClockwise() }

    fun mirror() = updateTransform { it.mirroredHorizontally() }

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

    private fun updateTransform(change: (ImageTransform) -> ImageTransform) =
        updateRecipe(_recipe.value.copy(transform = change(_recipe.value.transform)))

    private fun updateRecipe(recipe: EditRecipe) {
        _recipe.value = recipe
        savedStateHandle[KEY_FLIPPED] = recipe.transform.flipped
        savedStateHandle[KEY_ROTATION] = recipe.transform.rotationDegrees
    }

    private fun restoreRecipe(): EditRecipe = EditRecipe(
        transform = ImageTransform(
            flipped = savedStateHandle[KEY_FLIPPED] ?: false,
            rotationDegrees = savedStateHandle[KEY_ROTATION] ?: 0,
        ),
    )

    private companion object {
        const val KEY_FLIPPED = "recipe_flipped"
        const val KEY_ROTATION = "recipe_rotation"
    }
}
