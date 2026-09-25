package dev.ybdn.ciao.galerie.presentation.triage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciao.galerie.R
import dev.ybdn.ciao.galerie.domain.model.GalleryItem
import dev.ybdn.ciao.galerie.domain.model.TriageDecision
import dev.ybdn.ciao.galerie.domain.model.TriageSummary
import dev.ybdn.ciao.galerie.presentation.galerieViewModel
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoNotice
import dev.ybdn.ciao.designsystem.components.NeoStat
import dev.ybdn.ciao.designsystem.components.NeoTone
import dev.ybdn.ciao.designsystem.components.NeoTopBar
import dev.ybdn.ciao.galerie.presentation.gallery.formatDay
import dev.ybdn.ciao.designsystem.theme.LabelMono
import dev.ybdn.ciao.designsystem.theme.NeoTheme
import dev.ybdn.ciao.galerie.presentation.util.formatBytes
import kotlinx.coroutines.launch

/** Onglet « Trier » : un média à la fois, garder / supprimer / revoir plus tard (spec v4). */
@Composable
fun TriageScreen(onOpenDeletionQueue: () -> Unit) {
    val viewModel = galerieViewModel { container, app -> TriageViewModel(container, app) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val ssdAvailable by viewModel.ssdAvailable.collectAsStateWithLifecycle()
    var showSummary by rememberSaveable { mutableStateOf(false) }
    val current = uiState.current

    BackHandler(enabled = showSummary && current != null) { showSummary = false }
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NeoTopBar(
            title = stringResource(R.string.triage_title),
            actions = {
                if (uiState.canUndo && !showSummary) {
                    IconButton(onClick = viewModel::undo) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = stringResource(R.string.triage_undo))
                    }
                }
                if (current != null && !showSummary) {
                    IconButton(onClick = { showSummary = true }) {
                        Icon(Icons.Outlined.Assessment, contentDescription = stringResource(R.string.triage_summary))
                    }
                }
            },
        )

        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            current == null || showSummary -> TriageSummaryContent(
                summary = summary,
                pileEmpty = current == null,
                onOpenDeletionQueue = onOpenDeletionQueue,
                onBack = if (current != null) ({ showSummary = false }) else null,
                onUndo = if (current == null && uiState.canUndo) viewModel::undo else null,
            )
            else -> TriagePile(
                current = current,
                next = uiState.next,
                remaining = uiState.remaining,
                remainingInDay = uiState.remainingInDay,
                ssdAvailable = ssdAvailable,
                resolveOriginal = viewModel::originalUri,
                onDecide = viewModel::decide,
            )
        }
    }
}

@Composable
private fun TriagePile(
    current: GalleryItem,
    next: GalleryItem?,
    remaining: Int,
    remainingInDay: Int,
    ssdAvailable: Boolean,
    resolveOriginal: suspend (GalleryItem) -> String?,
    onDecide: (GalleryItem, TriageDecision) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val cardState = remember(current.key) { SwipeCardState() }
    val decide: (TriageDecision) -> Unit = { decision -> onDecide(current, decision) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(formatDay(current.captureDate), style = MaterialTheme.typography.titleMedium, color = NeoTheme.palette.content)
            Text(
                text = pluralStringResource(R.plurals.triage_remaining_in_day, remainingInDay, remainingInDay) +
                    " · " + pluralStringResource(R.plurals.triage_remaining, remaining, remaining),
                style = LabelMono,
                color = NeoTheme.palette.content,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (next != null) {
                // Carte suivante, en retrait sous la carte courante : son image est déjà chargée.
                TriageCard(
                    item = next,
                    state = null,
                    ssdAvailable = ssdAvailable,
                    resolveOriginal = resolveOriginal,
                    onDecide = {},
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = NEXT_CARD_SCALE
                            scaleY = NEXT_CARD_SCALE
                        },
                )
            }
            TriageCard(
                item = current,
                state = cardState,
                ssdAvailable = ssdAvailable,
                resolveOriginal = resolveOriginal,
                onDecide = decide,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Boutons équivalents aux gestes, pour l'accessibilité et la précision.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
        ) {
            listOf(
                Triple(TriageDecision.QUEUED_FOR_DELETION, R.string.triage_delete, NeoTone.Danger),
                Triple(TriageDecision.SNOOZED, R.string.triage_later, NeoTone.Warning),
                Triple(TriageDecision.KEPT, R.string.triage_keep, NeoTone.Success),
            ).forEach { (decision, label, tone) ->
                NeoButton(
                    text = stringResource(label),
                    onClick = { scope.launch { cardState.fling(decision, decide) } },
                    tone = tone,
                    enabled = !cardState.isSettling,
                    horizontalPadding = 8.dp,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * Bilan : compteurs de la session, puis accès à la file de suppression (qui peut contenir des
 * médias de sessions précédentes). Sert aussi d'état vide quand il n'y a plus rien à trier.
 */
@Composable
private fun TriageSummaryContent(
    summary: TriageSummary?,
    pileEmpty: Boolean,
    onOpenDeletionQueue: () -> Unit,
    onBack: (() -> Unit)?,
    onUndo: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (summary == null) {
            CircularProgressIndicator()
            return@Column
        }
        val sessionDecisions = summary.kept + summary.queued + summary.snoozed
        Text(
            text = stringResource(
                when {
                    !pileEmpty -> R.string.triage_summary_title
                    sessionDecisions > 0 -> R.string.triage_done_title
                    else -> R.string.triage_empty_title
                },
            ),
            style = MaterialTheme.typography.titleLarge,
            color = NeoTheme.palette.content,
        )
        if (pileEmpty) {
            Text(stringResource(R.string.triage_empty_hint), style = MaterialTheme.typography.bodyMedium, color = NeoTheme.palette.content)
        }

        if (sessionDecisions > 0 || !pileEmpty) {
            NeoStat(
                value = summary.kept.toString(),
                caption = pluralStringResource(R.plurals.triage_stat_kept, summary.kept),
                tone = NeoTone.Success,
                modifier = Modifier.fillMaxWidth(),
            )
            NeoStat(
                value = summary.queued.toString(),
                caption = pluralStringResource(R.plurals.triage_stat_queued, summary.queued),
                tone = NeoTone.Danger,
                modifier = Modifier.fillMaxWidth(),
            )
            if (summary.queued > 0) {
                Text(
                    stringResource(R.string.triage_stat_queued_size, formatBytes(summary.queuedBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeoTheme.palette.content,
                )
            }
            NeoStat(
                value = summary.snoozed.toString(),
                caption = stringResource(R.string.triage_stat_snoozed),
                tone = NeoTone.Warning,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (summary.totalQueued > 0) {
            NeoNotice(
                pluralStringResource(
                    R.plurals.triage_queue_pending,
                    summary.totalQueued,
                    summary.totalQueued,
                    formatBytes(summary.totalQueuedBytes),
                ),
                tone = NeoTone.Warning,
            )
            NeoButton(stringResource(R.string.triage_open_queue), onClick = onOpenDeletionQueue, tone = NeoTone.Danger)
        }
        if (onUndo != null) {
            NeoButton(stringResource(R.string.triage_undo), onClick = onUndo, tone = NeoTone.Surface)
        }
        if (onBack != null) {
            NeoButton(stringResource(R.string.triage_back), onClick = onBack, tone = NeoTone.Surface)
        }
    }
}

private const val NEXT_CARD_SCALE = 0.94f
