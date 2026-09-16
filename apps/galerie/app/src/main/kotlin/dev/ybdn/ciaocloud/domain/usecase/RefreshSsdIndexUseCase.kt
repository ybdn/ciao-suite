package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.SsdMedia
import dev.ybdn.ciaocloud.domain.repository.SsdFileEntry
import dev.ybdn.ciaocloud.domain.repository.SsdMediaBrowser
import dev.ybdn.ciaocloud.domain.repository.SsdMediaIndex
import dev.ybdn.ciaocloud.domain.util.MediaFileTypes
import dev.ybdn.ciaocloud.domain.util.TimelineBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface SsdIndexState {
    data object Idle : SsdIndexState
    data class Running(val dayFoldersScanned: Int, val filesFound: Int) : SsdIndexState
    data class Done(val indexedCount: Int) : SsdIndexState
    data object DestinationUnavailable : SsdIndexState
    data class Failed(val reason: String) : SsdIndexState
}

/**
 * Réindexation complète du SSD, déclenchée manuellement uniquement. Tourne dans la portée de
 * l'application : quitter l'écran ne l'interrompt pas. L'instant de prise de vue déjà connu (fichiers
 * transférés par l'app) est conservé ; les fichiers disparus sont retirés de l'index.
 */
class RefreshSsdIndexUseCase(
    private val ssdMediaBrowser: SsdMediaBrowser,
    private val ssdMediaIndex: SsdMediaIndex,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SsdIndexState>(SsdIndexState.Idle)
    val state: StateFlow<SsdIndexState> = _state.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        _state.value = SsdIndexState.Running(0, 0)
        job = scope.launch {
            _state.value = try {
                val files = ssdMediaBrowser.listDayFolderMedia { folders, found ->
                    _state.value = SsdIndexState.Running(folders, found)
                }
                if (files == null) {
                    SsdIndexState.DestinationUnavailable
                } else {
                    val known = ssdMediaIndex.observeAll().first().associateBy { it.relativePath.lowercase() }
                    val media = buildIndex(files, known)
                    ssdMediaIndex.replaceAll(media)
                    SsdIndexState.Done(media.size)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SsdIndexState.Failed(e.message ?: e::class.simpleName ?: "Erreur inconnue")
            }
        }
    }

    private fun buildIndex(
        files: List<SsdFileEntry>,
        known: Map<String, SsdMedia>,
    ): List<SsdMedia> = files.mapNotNull { file ->
        val name = file.relativePath.substringAfterLast('/')
        val type = MediaFileTypes.fromFileName(name) ?: return@mapNotNull null
        val day = TimelineBuilder.parseDayFromPath(file.relativePath) ?: return@mapNotNull null
        SsdMedia(
            relativePath = file.relativePath,
            displayName = name,
            mediaType = type.mediaType,
            mimeType = type.mimeType,
            sizeBytes = file.sizeBytes,
            captureDate = day,
            capturedAtEpochMillis = known[file.relativePath.lowercase()]?.capturedAtEpochMillis,
            lastModifiedEpochMillis = file.lastModifiedEpochMillis,
        )
    }
}
