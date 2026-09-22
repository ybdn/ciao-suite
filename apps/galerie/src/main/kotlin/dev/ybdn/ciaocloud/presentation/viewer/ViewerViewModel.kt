package dev.ybdn.ciaocloud.presentation.viewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ybdn.ciaocloud.di.AppContainer
import dev.ybdn.ciaocloud.domain.model.EditCapabilities
import dev.ybdn.ciaocloud.domain.model.FavoriteKeys
import dev.ybdn.ciaocloud.domain.model.GalleryFilter
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.GeoPoint
import dev.ybdn.ciaocloud.domain.model.MetadataChanges
import dev.ybdn.ciaocloud.domain.model.MetadataEditSummary
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dev.ybdn.ciaocloud.domain.model.MediaDetails
import dev.ybdn.ciaocloud.domain.util.TimelineBuilder
import dev.ybdn.ciaocloud.presentation.gallery.GalleryActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/** Ce que parcourt la visionneuse. */
sealed interface ViewerSource {
    /** Chronologie de la galerie, filtrée, ouverte sur [initialKey]. */
    data class Timeline(val initialKey: String, val filter: GalleryFilter) : ViewerSource

    /** URI reçues d'une autre app ; [secure] : appareil verrouillé (`REVIEW_SECURE`). */
    data class External(val uris: List<String>, val mimeTypeHint: String?, val secure: Boolean) : ViewerSource
}

data class ViewerUiState(
    val isLoading: Boolean = true,
    val items: List<GalleryItem> = emptyList(),
    /** Page d'ouverture, calculée une seule fois : la liste peut ensuite changer (suppression). */
    val initialIndex: Int = 0,
)

class ViewerViewModel(
    private val appContainer: AppContainer,
    application: Application,
    val source: ViewerSource,
) : AndroidViewModel(application) {

    private var resolvedInitialIndex: Int? = null

    val uiState: StateFlow<ViewerUiState> = when (source) {
        is ViewerSource.Timeline -> timelineState(source)
        // Une URI MediaStore reprend l'élément de la chronologie : état de sauvegarde et favori à jour.
        is ViewerSource.External -> combine(
            flow { emit(appContainer.resolveExternalMediaUseCase(source.uris, source.mimeTypeHint)) },
            appContainer.observeTimelineUseCase(),
        ) { external, timeline ->
            val byKey = timeline.associateBy { it.key }
            val items = external.map { item -> byKey[FavoriteKeys.phone(item.phone!!.mediaStoreId)] ?: item }
            ViewerUiState(isLoading = false, items = items)
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewerUiState())

    val actions = GalleryActions(appContainer, viewModelScope)

    val ssdAvailable: StateFlow<Boolean> = appContainer.observeSsdAvailabilityUseCase()

    val transferRunning: StateFlow<Boolean> = appContainer.getEditCapabilitiesUseCase.transferRunning

    val clipboardLocation: StateFlow<GeoPoint?> = appContainer.locationClipboard.location

    fun copyLocation(point: GeoPoint) = appContainer.locationClipboard.copy(point)

    private val _metadataSaving = MutableStateFlow(false)
    val metadataSaving: StateFlow<Boolean> = _metadataSaving.asStateFlow()

    private val _metadataResults = MutableSharedFlow<MetadataEditSummary>(extraBufferCapacity = 1)
    val metadataResults: SharedFlow<MetadataEditSummary> = _metadataResults.asSharedFlow()

    fun editMetadata(item: GalleryItem, changes: MetadataChanges) {
        if (_metadataSaving.value || changes.isEmpty) return
        _metadataSaving.value = true
        viewModelScope.launch {
            try {
                _metadataResults.emit(appContainer.editMetadataUseCase(listOf(item), changes))
            } finally {
                _metadataSaving.value = false
            }
        }
    }

    /** Actions d'édition de [item] ; à recalculer quand le SSD ou un transfert change d'état. */
    fun editCapabilities(item: GalleryItem): EditCapabilities = appContainer.getEditCapabilitiesUseCase(
        item,
        isExternal = !canEdit(item),
        isLocked = source is ViewerSource.External && source.secure,
    )

    /** Un média hors MediaStore (pièce jointe…) ne peut être ni supprimé ni mis en favori. */
    fun canEdit(item: GalleryItem): Boolean = item.ssd != null || (item.phone?.mediaStoreId ?: -1) >= 0

    suspend fun details(item: GalleryItem): MediaDetails? = appContainer.getMediaDetailsUseCase(item)

    /** URI de l'original, null si seul le SSD le détient et qu'il est débranché. */
    suspend fun originalUri(item: GalleryItem): String? = appContainer.getOriginalUriUseCase(item)

    /**
     * Juste après une prise de vue (`REVIEW`), le média peut ne pas encore figurer dans la
     * chronologie : on l'attend brièvement avant d'ouvrir sur le plus récent.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun timelineState(source: ViewerSource.Timeline): Flow<ViewerUiState> =
        appContainer.observeTimelineUseCase().transformLatest { items ->
            val filtered = TimelineBuilder.filter(items, source.filter)
            val known = resolvedInitialIndex
            if (known != null) {
                emit(ViewerUiState(isLoading = false, items = filtered, initialIndex = known))
                return@transformLatest
            }
            val index = filtered.indexOfFirst { it.key == source.initialKey }
            if (index < 0) delay(INITIAL_ITEM_WAIT_MS)
            val initialIndex = index.coerceAtLeast(0).also { resolvedInitialIndex = it }
            emit(ViewerUiState(isLoading = false, items = filtered, initialIndex = initialIndex))
        }

    private companion object {
        const val INITIAL_ITEM_WAIT_MS = 3_000L
    }
}
