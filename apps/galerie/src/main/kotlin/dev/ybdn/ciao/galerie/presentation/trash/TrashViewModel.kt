package dev.ybdn.ciao.galerie.presentation.trash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ybdn.ciao.galerie.di.AppContainer
import dev.ybdn.ciao.galerie.domain.repository.TrashedMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrashViewModel(
    private val appContainer: AppContainer,
    application: Application,
) : AndroidViewModel(application) {

    /** null tant que la corbeille n'a pas été lue. */
    val trashed: StateFlow<List<TrashedMedia>?> = appContainer.observeTrashUseCase()
        .map { list -> list.sortedByDescending { it.expiresAtEpochMillis } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    fun toggle(id: Long) = _selectedIds.update { if (id in it) it - id else it + id }

    fun selectAll() {
        _selectedIds.value = trashed.value.orEmpty().mapTo(HashSet()) { it.media.mediaStoreId }
    }

    fun restoreSelection() = viewModelScope.launch {
        val selected = selectedMedia()
        if (appContainer.restoreFromTrashUseCase(selected.associate { it.media.mediaStoreId to it.media.uri })) {
            _selectedIds.value = emptySet()
        }
    }

    fun deleteSelectionForever() = viewModelScope.launch {
        if (appContainer.deleteFromTrashUseCase(selectedMedia().map { it.media.uri })) {
            _selectedIds.value = emptySet()
        }
    }

    private fun selectedMedia(): List<TrashedMedia> {
        val ids = _selectedIds.value
        return trashed.value.orEmpty().filter { it.media.mediaStoreId in ids }
    }
}
