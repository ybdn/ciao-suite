package dev.ybdn.ciaocloud.presentation.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ybdn.ciaocloud.di.AppContainer
import dev.ybdn.ciaocloud.domain.model.GalleryFilter
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.util.TimelineBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import dev.ybdn.ciaocloud.domain.usecase.DeleteTarget
import kotlinx.coroutines.launch
import dev.ybdn.ciaocloud.domain.usecase.SsdIndexState
import java.time.LocalDate

/** Case de la grille : en-tête de jour (pleine largeur) ou média. */
sealed interface GalleryEntry {
    val key: String

    data class DayHeader(val date: LocalDate) : GalleryEntry {
        override val key: String get() = "day:$date"
    }

    data class Media(val item: GalleryItem) : GalleryEntry {
        override val key: String get() = item.key
    }
}

data class GalleryUiState(
    val isLoading: Boolean = true,
    val filter: GalleryFilter = GalleryFilter.ALL,
    val entries: List<GalleryEntry> = emptyList(),
    /** Nombre total de médias, tous filtres confondus (distingue « aucun média » de « filtre vide »). */
    val totalCount: Int = 0,
)

class GalleryViewModel(
    private val appContainer: AppContainer,
    application: Application,
) : AndroidViewModel(application) {

    private val filter = MutableStateFlow(GalleryFilter.ALL)

    val uiState: StateFlow<GalleryUiState> = combine(appContainer.observeTimelineUseCase(), filter) { items, filter ->
        val entries = ArrayList<GalleryEntry>()
        TimelineBuilder.groupByDay(TimelineBuilder.filter(items, filter)).forEach { day ->
            entries += GalleryEntry.DayHeader(day.date)
            day.items.mapTo(entries) { GalleryEntry.Media(it) }
        }
        GalleryUiState(isLoading = false, filter = filter, entries = entries, totalCount = items.size)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    fun onFilterSelected(filter: GalleryFilter) {
        this.filter.value = filter
    }

    val ssdAvailable: StateFlow<Boolean> = appContainer.observeSsdAvailabilityUseCase()

    val ssdIndexState: StateFlow<SsdIndexState> = appContainer.refreshSsdIndexUseCase.state

    fun refresh() = appContainer.observeTimelineUseCase.refresh()

    fun onResume() {
        viewModelScope.launch { appContainer.observeSsdAvailabilityUseCase.refresh() }
    }

    val actions = GalleryActions(appContainer, viewModelScope)

    private val _selectedKeys = MutableStateFlow<Set<String>>(emptySet())
    /** Clés des éléments sélectionnés ; la sélection est active dès qu'elle n'est pas vide. */
    val selectedKeys: StateFlow<Set<String>> = _selectedKeys.asStateFlow()

    fun toggleSelection(key: String) {
        _selectedKeys.update { if (key in it) it - key else it + key }
    }

    fun clearSelection() {
        _selectedKeys.value = emptySet()
    }

    /** Éléments sélectionnés encore présents (une suppression externe peut en retirer). */
    private fun selectedItems(): List<GalleryItem> {
        val keys = _selectedKeys.value
        return uiState.value.entries.mapNotNull { (it as? GalleryEntry.Media)?.item?.takeIf { item -> item.key in keys } }
    }

    fun selectedItemsSnapshot(): List<GalleryItem> = selectedItems()

    fun shareSelection() = actions.share(selectedItems())

    fun toggleFavoriteOnSelection() = actions.toggleFavorite(selectedItems())

    fun deleteSelection(target: DeleteTarget) = actions.delete(selectedItems(), target, onDone = ::clearSelection)

    /** « Actualiser le SSD » : réindexation complète, déclenchée manuellement. */
    fun refreshSsdIndex() = appContainer.refreshSsdIndexUseCase.start()
}
