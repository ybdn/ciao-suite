package dev.ybdn.ciao.galerie.presentation.triage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ybdn.ciao.galerie.di.AppContainer
import dev.ybdn.ciao.galerie.domain.model.GalleryItem
import dev.ybdn.ciao.galerie.domain.model.TriageDecision
import dev.ybdn.ciao.galerie.domain.model.TriagePile
import dev.ybdn.ciao.galerie.domain.model.TriageSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TriageUiState(
    val isLoading: Boolean = true,
    val current: GalleryItem? = null,
    /** Carte suivante, affichée sous la carte courante (et préchargée). */
    val next: GalleryItem? = null,
    val remaining: Int = 0,
    /** Médias restant à trier pour le jour de la carte courante. */
    val remainingInDay: Int = 0,
    val canUndo: Boolean = false,
)

/** Dernière décision de la session, seule annulable. */
private data class LastDecision(val itemKey: String, val triageKey: String)

class TriageViewModel(
    private val appContainer: AppContainer,
    application: Application,
) : AndroidViewModel(application) {

    /** Début de la session : le bilan compte les décisions prises depuis l'ouverture de l'écran. */
    private val sessionStartEpochMillis = System.currentTimeMillis()

    /**
     * Médias tranchés que la pile observée contient encore : la carte suivante s'affiche tout de
     * suite, sans attendre que la base notifie l'écriture. Une clé en sort dès que la pile ne la
     * contient plus.
     */
    private val hiddenKeys = MutableStateFlow<Set<String>>(emptySet())

    private val lastDecision = MutableStateFlow<LastDecision?>(null)

    private val pile: StateFlow<TriagePile?> = appContainer.observeTriagePileUseCase()
        .onEach { pile ->
            val pendingKeys = pile.pending.mapTo(HashSet()) { it.key }
            hiddenKeys.update { hidden -> hidden.filterTo(HashSet()) { it in pendingKeys } }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<TriageUiState> = combine(pile, hiddenKeys, lastDecision) { pile, hidden, last ->
        if (pile == null) return@combine TriageUiState()
        val pending = if (hidden.isEmpty()) pile.pending else pile.pending.filter { it.key !in hidden }
        val current = pending.firstOrNull()
        TriageUiState(
            isLoading = false,
            current = current,
            next = pending.getOrNull(1),
            remaining = pending.size,
            remainingInDay = current?.let { item -> pending.count { it.captureDate == item.captureDate } } ?: 0,
            canUndo = last != null,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TriageUiState())

    val summary: StateFlow<TriageSummary?> = appContainer.getTriageSummaryUseCase(sessionStartEpochMillis)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val ssdAvailable: StateFlow<Boolean> = appContainer.observeSsdAvailabilityUseCase()

    fun onResume() {
        viewModelScope.launch { appContainer.observeSsdAvailabilityUseCase.refresh() }
    }

    /** Tranche la carte [item] ; ignoré si ce n'est plus la carte courante (double geste). */
    fun decide(item: GalleryItem, decision: TriageDecision) {
        if (uiState.value.current?.key != item.key || item.key in hiddenKeys.value) return
        hiddenKeys.update { it + item.key }
        viewModelScope.launch {
            val triageKey = appContainer.recordTriageDecisionUseCase(item, decision)
            lastDecision.value = LastDecision(item.key, triageKey)
        }
    }

    /** Annule la dernière décision : la carte revient en tête de pile. */
    fun undo() {
        val last = lastDecision.value ?: return
        lastDecision.value = null
        viewModelScope.launch {
            appContainer.undoLastTriageDecisionUseCase(last.triageKey)
            hiddenKeys.update { it - last.itemKey }
        }
    }

    suspend fun originalUri(item: GalleryItem): String? = appContainer.getOriginalUriUseCase(item)
}
