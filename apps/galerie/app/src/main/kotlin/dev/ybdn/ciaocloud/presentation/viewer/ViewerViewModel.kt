package dev.ybdn.ciaocloud.presentation.viewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ybdn.ciaocloud.di.AppContainer
import dev.ybdn.ciaocloud.domain.model.GalleryFilter
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.MediaDetails
import dev.ybdn.ciaocloud.domain.util.TimelineBuilder
import dev.ybdn.ciaocloud.presentation.gallery.GalleryActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ViewerUiState(
    val isLoading: Boolean = true,
    val items: List<GalleryItem> = emptyList(),
    /** Page d'ouverture, calculée une seule fois : la liste peut ensuite changer (suppression). */
    val initialIndex: Int = 0,
)

class ViewerViewModel(
    private val appContainer: AppContainer,
    application: Application,
    private val initialKey: String,
    filter: GalleryFilter,
) : AndroidViewModel(application) {

    private var resolvedInitialIndex: Int? = null

    val uiState: StateFlow<ViewerUiState> = appContainer.observeTimelineUseCase()
        .map { items ->
            val filtered = TimelineBuilder.filter(items, filter)
            val initialIndex = resolvedInitialIndex
                ?: filtered.indexOfFirst { it.key == initialKey }.coerceAtLeast(0).also { resolvedInitialIndex = it }
            ViewerUiState(isLoading = false, items = filtered, initialIndex = initialIndex)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewerUiState())

    val actions = GalleryActions(appContainer, viewModelScope)

    val ssdAvailable: StateFlow<Boolean> = appContainer.observeSsdAvailabilityUseCase()

    suspend fun details(item: GalleryItem): MediaDetails? = appContainer.getMediaDetailsUseCase(item)

    /** URI de l'original, null si seul le SSD le détient et qu'il est débranché. */
    suspend fun originalUri(item: GalleryItem): String? = appContainer.getOriginalUriUseCase(item)
}
