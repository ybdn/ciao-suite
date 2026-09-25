package dev.ybdn.ciaocloud.presentation.gallery

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.di.AppContainer
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.GalleryLocation
import dev.ybdn.ciaocloud.domain.repository.ShareableMedia
import dev.ybdn.ciaocloud.domain.usecase.DeleteItemsOutcome
import dev.ybdn.ciaocloud.domain.usecase.DeleteTarget
import dev.ybdn.ciaocloud.domain.usecase.ShareOutcome
import dev.ybdn.ciao.designsystem.components.NeoProgressBar
import dev.ybdn.ciaocloud.presentation.util.formatBytes
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoCard
import dev.ybdn.ciao.designsystem.components.NeoNotice
import dev.ybdn.ciao.designsystem.components.NeoTone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Retour d'une action de la galerie, affiché brièvement. */
sealed interface GalleryEvent {
    data class Share(val media: List<ShareableMedia>) : GalleryEvent
    data class Deleted(val outcome: DeleteItemsOutcome) : GalleryEvent
    data object ShareSsdUnavailable : GalleryEvent
    data class ShareInsufficientSpace(val requiredBytes: Long) : GalleryEvent
    data class Error(val message: String) : GalleryEvent
}

/** Dialogue bloquant d'un partage sans métadonnées. */
sealed interface ShareDialogState {
    /** Retrait des métadonnées du média [current] sur [total]. */
    data class Progress(val current: Int, val total: Int) : ShareDialogState

    /** Médias non nettoyés : partager les autres ou annuler. */
    data class Failures(val outcome: ShareOutcome.PartiallyFailed) : ShareDialogState
}

/** Actions communes à la grille et à la visionneuse : partager, favori, supprimer. */
class GalleryActions(
    private val appContainer: AppContainer,
    private val scope: CoroutineScope,
) {
    private val _isBusy = MutableStateFlow(false)
    /** Opération longue en cours (copie pour partage, suppression). */
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _events = MutableSharedFlow<GalleryEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<GalleryEvent> = _events.asSharedFlow()

    private val _shareDialog = MutableStateFlow<ShareDialogState?>(null)
    val shareDialog: StateFlow<ShareDialogState?> = _shareDialog.asStateFlow()

    private var shareJob: Job? = null

    fun share(items: List<GalleryItem>) {
        shareJob = runBusy {
            try {
                val outcome = appContainer.prepareShareUseCase(items) { current, total ->
                    _shareDialog.value = ShareDialogState.Progress(current, total)
                }
                _shareDialog.value = null
                when (outcome) {
                    is ShareOutcome.Ready -> _events.emit(GalleryEvent.Share(outcome.media))
                    is ShareOutcome.PartiallyFailed -> _shareDialog.value = ShareDialogState.Failures(outcome)
                    is ShareOutcome.InsufficientSpace -> _events.emit(GalleryEvent.ShareInsufficientSpace(outcome.requiredBytes))
                    ShareOutcome.SsdUnavailable -> _events.emit(GalleryEvent.ShareSsdUnavailable)
                }
            } finally {
                if (_shareDialog.value is ShareDialogState.Progress) _shareDialog.value = null
            }
        }
    }

    /** Annule la préparation en cours : les copies déjà produites sont supprimées, rien n'est partagé. */
    fun cancelShare() {
        shareJob?.cancel()
    }

    /** Partage les médias nettoyés malgré l'échec des autres. */
    fun shareRemaining() {
        val failures = _shareDialog.value as? ShareDialogState.Failures ?: return
        _shareDialog.value = null
        _events.tryEmit(GalleryEvent.Share(failures.outcome.media))
    }

    fun dismissShareFailures() {
        val failures = _shareDialog.value as? ShareDialogState.Failures ?: return
        _shareDialog.value = null
        scope.launch { appContainer.prepareShareUseCase.discard(failures.outcome) }
    }

    fun toggleFavorite(items: List<GalleryItem>) {
        scope.launch { appContainer.toggleFavoriteUseCase(items) }
    }

    fun delete(items: List<GalleryItem>, target: DeleteTarget, onDone: () -> Unit = {}) {
        runBusy {
            val outcome = appContainer.deleteGalleryItemsUseCase(items, target)
            _events.emit(GalleryEvent.Deleted(outcome))
            if (outcome is DeleteItemsOutcome.Done) onDone()
        }
    }

    private fun runBusy(block: suspend () -> Unit): Job? {
        if (_isBusy.value) return null
        _isBusy.value = true
        return scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(GalleryEvent.Error(e.message ?: e::class.simpleName.orEmpty()))
            } finally {
                _isBusy.value = false
            }
        }
    }
}

/** Réagit aux événements : feuille de partage système, messages courts. */
@Composable
fun GalleryEventsEffect(events: SharedFlow<GalleryEvent>) {
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is GalleryEvent.Share -> context.shareMedia(event.media)
                is GalleryEvent.Deleted -> context.deletedMessage(event.outcome)?.let { context.toast(it) }
                GalleryEvent.ShareSsdUnavailable -> context.toast(resources.getString(R.string.gallery_share_ssd_unavailable))
                is GalleryEvent.ShareInsufficientSpace ->
                    context.toast(resources.getString(R.string.share_strip_insufficient_space, formatBytes(event.requiredBytes)))
                is GalleryEvent.Error -> context.toast(resources.getString(R.string.gallery_action_error, event.message))
            }
        }
    }
}

private fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

private fun Context.deletedMessage(outcome: DeleteItemsOutcome): String? = when (outcome) {
    DeleteItemsOutcome.Cancelled -> null
    DeleteItemsOutcome.SsdUnavailable -> getString(R.string.gallery_delete_ssd_unavailable)
    is DeleteItemsOutcome.Done -> buildList {
        if (outcome.phoneTrashed > 0) add(resources.getQuantityString(R.plurals.gallery_deleted_phone, outcome.phoneTrashed, outcome.phoneTrashed))
        if (outcome.ssdDeleted > 0) add(resources.getQuantityString(R.plurals.gallery_deleted_ssd, outcome.ssdDeleted, outcome.ssdDeleted))
        if (outcome.ssdFailed > 0) add(resources.getQuantityString(R.plurals.gallery_deleted_ssd_failed, outcome.ssdFailed, outcome.ssdFailed))
    }.joinToString("\n").ifEmpty { null }
}

/** `ACTION_SEND(_MULTIPLE)` avec droit de lecture accordé sur chaque URI. */
private fun Context.shareMedia(media: List<ShareableMedia>) {
    if (media.isEmpty()) return
    val uris = media.map { Uri.parse(it.uri) }
    val mimeTypes = media.map { it.mimeType }.distinct()
    val mimeType = when {
        mimeTypes.size == 1 -> mimeTypes.single()
        mimeTypes.all { it.startsWith("image/") } -> "image/*"
        mimeTypes.all { it.startsWith("video/") } -> "video/*"
        else -> "*/*"
    }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }.apply {
        type = mimeType
        clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * Choix de suppression selon l'emplacement des éléments. Toute suppression du SSD, définitive,
 * demande une seconde confirmation explicite.
 */
@Composable
fun DeleteItemsDialog(
    items: List<GalleryItem>,
    onDelete: (DeleteTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    val hasPhone = items.any { it.phone != null }
    val hasSsd = items.any { it.ssd != null }
    val notBackedUp = items.count { it.location == GalleryLocation.PHONE }
    var pendingSsdTarget by remember { mutableStateOf<DeleteTarget?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        NeoCard(modifier = Modifier.padding(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val target = pendingSsdTarget
                if (target == null) {
                    Text(
                        pluralStringResource(R.plurals.gallery_delete_title, items.size, items.size),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (notBackedUp > 0) {
                        NeoNotice(pluralStringResource(R.plurals.gallery_delete_not_backed_up, notBackedUp, notBackedUp), tone = NeoTone.Warning)
                    }
                    if (hasPhone) {
                        NeoButton(stringResource(R.string.gallery_delete_phone), onClick = { onDelete(DeleteTarget.PHONE) }, tone = NeoTone.Danger)
                        Text(stringResource(R.string.gallery_delete_phone_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    if (hasSsd) {
                        NeoButton(
                            stringResource(R.string.gallery_delete_ssd),
                            onClick = { pendingSsdTarget = DeleteTarget.SSD },
                            tone = NeoTone.Danger,
                        )
                    }
                    if (hasPhone && hasSsd) {
                        NeoButton(
                            stringResource(R.string.gallery_delete_everywhere),
                            onClick = { pendingSsdTarget = DeleteTarget.EVERYWHERE },
                            tone = NeoTone.Danger,
                        )
                    }
                    NeoButton(stringResource(R.string.delete_confirm_cancel), onClick = onDismiss, tone = NeoTone.Surface)
                } else {
                    Text(stringResource(R.string.gallery_delete_ssd_confirm_title), style = MaterialTheme.typography.titleLarge)
                    NeoNotice(
                        pluralStringResource(
                            R.plurals.gallery_delete_ssd_confirm,
                            items.count { it.ssd != null },
                            items.count { it.ssd != null },
                        ),
                        tone = NeoTone.Danger,
                    )
                    NeoButton(
                        stringResource(R.string.gallery_delete_ssd_confirm_button),
                        onClick = { onDelete(target) },
                        tone = NeoTone.Danger,
                    )
                    NeoButton(
                        stringResource(R.string.delete_confirm_cancel),
                        onClick = { pendingSsdTarget = null },
                        tone = NeoTone.Surface,
                    )
                }
            }
        }
    }
}

/**
 * Dialogues du partage sans métadonnées : progression bloquante avec annulation, puis bilan des
 * médias non nettoyés. Jamais de repli vers l'original.
 */
@Composable
fun ShareDialogs(actions: GalleryActions) {
    val state by actions.shareDialog.collectAsStateWithLifecycle()
    when (val dialog = state) {
        null -> Unit
        is ShareDialogState.Progress -> Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            NeoCard(modifier = Modifier.padding(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.share_strip_progress, dialog.current, dialog.total),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    NeoProgressBar(progress = (dialog.current - 1).toFloat() / dialog.total)
                    NeoButton(stringResource(R.string.delete_confirm_cancel), onClick = actions::cancelShare, tone = NeoTone.Surface)
                }
            }
        }
        is ShareDialogState.Failures -> Dialog(onDismissRequest = actions::dismissShareFailures) {
            val failed = dialog.outcome.failedNames
            NeoCard(modifier = Modifier.padding(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        pluralStringResource(R.plurals.share_strip_failed_title, failed.size, failed.size),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    NeoNotice(failed.joinToString("\n"), tone = NeoTone.Danger)
                    if (dialog.outcome.media.isNotEmpty()) {
                        NeoButton(stringResource(R.string.share_strip_share_others), onClick = actions::shareRemaining, tone = NeoTone.Primary)
                    }
                    NeoButton(
                        stringResource(R.string.delete_confirm_cancel),
                        onClick = actions::dismissShareFailures,
                        tone = NeoTone.Surface,
                    )
                }
            }
        }
    }
}
