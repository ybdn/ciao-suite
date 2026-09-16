package dev.ybdn.ciaocloud.data.saf

import android.content.Context
import android.net.Uri
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import dev.ybdn.ciaocloud.domain.repository.SsdFileEntry
import dev.ybdn.ciaocloud.domain.repository.SsdMediaBrowser
import dev.ybdn.ciaocloud.domain.util.MediaFileTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/**
 * Parcours du SSD via SAF pour la galerie. La disponibilité est revérifiée à chaque changement
 * d'état d'un volume de stockage (branchement, débranchement, éjection).
 */
class SafSsdMediaBrowser(
    private val context: Context,
    private val rootUriProvider: suspend () -> Uri?,
    private val scope: CoroutineScope,
) : SsdMediaBrowser {

    private val resolver get() = context.contentResolver

    private val _isAvailable = MutableStateFlow(false)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    init {
        context.getSystemService(StorageManager::class.java).registerStorageVolumeCallback(
            ContextCompat.getMainExecutor(context),
            object : StorageManager.StorageVolumeCallback() {
                override fun onStateChanged(volume: StorageVolume) {
                    scope.launch { refreshAvailability() }
                }
            },
        )
        scope.launch { refreshAvailability() }
    }

    override suspend fun refreshAvailability(): Boolean = withContext(Dispatchers.IO) {
        val rootUri = rootUriProvider()
        val available = rootUri != null &&
            resolver.persistedUriPermissions.any { it.uri == rootUri && it.isReadPermission } &&
            runCatching { SafDocuments.displayName(resolver, SafDocuments.rootDocumentUri(rootUri)) != null }
                .getOrDefault(false)
        _isAvailable.value = available
        available
    }

    override suspend fun listDayFolderMedia(
        onProgress: (dayFoldersScanned: Int, filesFound: Int) -> Unit,
    ): List<SsdFileEntry>? = withContext(Dispatchers.IO) {
        if (!refreshAvailability()) return@withContext null
        val rootUri = rootUriProvider() ?: return@withContext null
        val root = SafDocuments.rootDocumentUri(rootUri)

        val files = ArrayList<SsdFileEntry>()
        var dayFolders = 0
        fun subdirectories(uri: Uri, pattern: Regex) =
            SafDocuments.listChildren(resolver, uri).filter { it.isDirectory && pattern.matches(it.name) }

        for (dcim in subdirectories(root, DCIM)) {
            for (year in subdirectories(dcim.uri, YEAR)) {
                for (month in subdirectories(year.uri, TWO_DIGITS)) {
                    for (day in subdirectories(month.uri, TWO_DIGITS)) {
                        ensureActive()
                        val prefix = "${dcim.name}/${year.name}/${month.name}/${day.name}/"
                        SafDocuments.listChildren(resolver, day.uri)
                            .filter { !it.isDirectory && MediaFileTypes.fromFileName(it.name) != null }
                            .mapTo(files) { SsdFileEntry(prefix + it.name, it.sizeBytes, it.lastModifiedMillis) }
                        dayFolders++
                        onProgress(dayFolders, files.size)
                    }
                }
            }
        }
        files
    }

    override suspend fun documentUri(relativePath: String): String? = withContext(Dispatchers.IO) {
        if (!_isAvailable.value) return@withContext null
        resolveDocumentUri(relativePath)?.toString()
    }

    override suspend fun delete(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        if (!refreshAvailability()) return@withContext false
        val uri = resolveDocumentUri(relativePath) ?: return@withContext true
        try {
            DocumentsContract.deleteDocument(resolver, uri)
        } catch (e: FileNotFoundException) {
            true
        } catch (e: Exception) {
            // Certains providers signalent un document absent par une IllegalArgumentException.
            SafDocuments.displayName(resolver, uri) == null
        }
    }

    private suspend fun resolveDocumentUri(relativePath: String): Uri? {
        val rootUri = rootUriProvider() ?: return null
        return SafDocuments.resolve(resolver, rootUri, relativePath)
    }

    private companion object {
        val DCIM = Regex("dcim", RegexOption.IGNORE_CASE)
        val YEAR = Regex("""\d{4}""")
        val TWO_DIGITS = Regex("""\d{2}""")
    }
}
