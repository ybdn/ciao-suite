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
import kotlinx.coroutines.flow.stateIn
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

    fun refresh() = appContainer.observeTimelineUseCase.refresh()
}
