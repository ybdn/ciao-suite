package dev.ybdn.ciaocloud.presentation.triageconfirm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ybdn.ciaocloud.di.AppContainer
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.TriageDeletionPlan
import dev.ybdn.ciaocloud.domain.usecase.DeleteItemsOutcome
import dev.ybdn.ciaocloud.presentation.gallery.GalleryEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TriageConfirmUiState(
    val isLoading: Boolean = true,
    val plan: TriageDeletionPlan = TriageDeletionPlan(emptyList(), emptyList()),
    val isDeleting: Boolean = false,
)

class TriageConfirmViewModel(
    private val appContainer: AppContainer,
    application: Application,
) : AndroidViewModel(application) {

    private val isDeleting = MutableStateFlow(false)

    val uiState: StateFlow<TriageConfirmUiState> =
        combine(appContainer.observeTriageDeletionQueueUseCase(), isDeleting) { plan, deleting ->
            TriageConfirmUiState(isLoading = false, plan = plan, isDeleting = deleting)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TriageConfirmUiState())

    val ssdAvailable: StateFlow<Boolean> = appContainer.observeSsdAvailabilityUseCase()

    /** Bilan de la suppression, affiché comme depuis la galerie. */
    private val _events = MutableSharedFlow<GalleryEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<GalleryEvent> = _events.asSharedFlow()

    /** Toute la file a été supprimée : l'écran peut se fermer. */
    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    fun onResume() {
        viewModelScope.launch { appContainer.observeSsdAvailabilityUseCase.refresh() }
    }

    /** Retire un média de la file : il redevient proposé au tri. */
    fun dequeue(item: GalleryItem) {
        viewModelScope.launch { appContainer.dequeueTriageDeletionUseCase(listOf(item)) }
    }

    fun confirmDeletion() {
        if (isDeleting.value) return
        val items = uiState.value.plan.all
        if (items.isEmpty()) return
        isDeleting.value = true
        viewModelScope.launch {
            try {
                val outcome = appContainer.deleteQueuedTriageItemsUseCase(items)
                _events.emit(GalleryEvent.Deleted(outcome))
                if (outcome is DeleteItemsOutcome.Done && outcome.completedKeys.size == items.size) _finished.emit(Unit)
            } finally {
                isDeleting.value = false
            }
        }
    }
}
