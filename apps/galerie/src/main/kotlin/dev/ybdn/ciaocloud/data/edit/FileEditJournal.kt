package dev.ybdn.ciaocloud.data.edit

import android.content.Context
import android.util.Log
import dev.ybdn.ciaocloud.domain.model.FileFingerprint
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.model.TransferRecord
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import dev.ybdn.ciaocloud.domain.repository.EditJournal
import dev.ybdn.ciaocloud.domain.repository.EditJournalEntry
import dev.ybdn.ciaocloud.domain.repository.EditStepKind
import dev.ybdn.ciaocloud.domain.repository.PhoneEditStep
import dev.ybdn.ciaocloud.domain.repository.SsdEditStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Journal de reprise : une opération = `filesDir/edit-journal/<id>.json`, écrit de façon atomique
 * (fichier temporaire synchronisé puis renommé). Les sauvegardes d'originaux du téléphone y vivent
 * aussi (`<id>.backup`).
 */
class FileEditJournal(
    private val context: Context,
) : EditJournal {

    private val directory get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }
    private val mutex = Mutex()

    override suspend fun write(entry: EditJournalEntry) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val temporary = File(directory, "${entry.id}$JSON_SUFFIX.tmp")
            temporary.outputStream().use { output ->
                output.write(entry.toJson().toString().toByteArray())
                output.fd.sync()
            }
            check(temporary.renameTo(File(directory, entry.id + JSON_SUFFIX))) { "Journal non écrit : ${entry.id}" }
        }
    }

    override suspend fun remove(id: String) {
        withContext(Dispatchers.IO) { mutex.withLock { File(directory, id + JSON_SUFFIX).delete() } }
    }

    override suspend fun entries(): List<EditJournalEntry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            directory.listFiles { file -> file.name.endsWith(JSON_SUFFIX) }.orEmpty().mapNotNull { file ->
                runCatching { JSONObject(file.readText()).toEntry() }
                    .onFailure { Log.e(TAG, "Entrée de journal illisible : ${file.name}", it) }
                    .getOrNull()
            }
        }
    }

    override fun backupPath(id: String): String = File(File(context.filesDir, DIRECTORY), "$id.backup").path

    private fun EditJournalEntry.toJson() = JSONObject().apply {
        put("id", id)
        phone?.let { step ->
            put("phone", JSONObject().apply {
                put("kind", step.kind.name)
                putOpt("uri", step.uri)
                put("new", step.newFingerprint.toJson())
                putOpt("originalChecksum", step.originalChecksum)
                putOpt("backupPath", step.backupPath)
            })
        }
        ssd?.let { step ->
            put("ssd", JSONObject().apply {
                put("kind", step.kind.name)
                put("relativePath", step.relativePath)
                put("new", step.newFingerprint.toJson())
                putOpt("originalChecksum", step.originalChecksum)
            })
        }
        put("previousRecords", JSONArray(previousRecords.map { it.toJson() }))
        putOpt("editedMediaStoreId", editedMediaStoreId)
    }

    private fun JSONObject.toEntry() = EditJournalEntry(
        id = getString("id"),
        phone = optJSONObject("phone")?.let {
            PhoneEditStep(
                kind = EditStepKind.valueOf(it.getString("kind")),
                uri = it.optStringOrNull("uri"),
                newFingerprint = it.getJSONObject("new").toFingerprint(),
                originalChecksum = it.optStringOrNull("originalChecksum"),
                backupPath = it.optStringOrNull("backupPath"),
            )
        },
        ssd = optJSONObject("ssd")?.let {
            SsdEditStep(
                kind = EditStepKind.valueOf(it.getString("kind")),
                relativePath = it.getString("relativePath"),
                newFingerprint = it.getJSONObject("new").toFingerprint(),
                originalChecksum = it.optStringOrNull("originalChecksum"),
            )
        },
        previousRecords = getJSONArray("previousRecords").let { array ->
            (0 until array.length()).map { array.getJSONObject(it).toRecord() }
        },
        editedMediaStoreId = if (has("editedMediaStoreId")) getLong("editedMediaStoreId") else null,
    )

    private fun FileFingerprint.toJson() = JSONObject().put("size", sizeBytes).put("checksum", checksum)

    private fun JSONObject.toFingerprint() = FileFingerprint(getLong("size"), getString("checksum"))

    private fun TransferRecord.toJson() = JSONObject().apply {
        put("mediaStoreId", mediaStoreId)
        put("mediaType", mediaType.name)
        put("status", status.name)
        putOpt("destinationPath", destinationPath)
        putOpt("checksum", checksum)
        putOpt("errorMessage", errorMessage)
        put("sizeBytes", sizeBytes)
    }

    private fun JSONObject.toRecord() = TransferRecord(
        mediaStoreId = getLong("mediaStoreId"),
        mediaType = MediaType.valueOf(getString("mediaType")),
        status = TransferStatus.valueOf(getString("status")),
        destinationPath = optStringOrNull("destinationPath"),
        checksum = optStringOrNull("checksum"),
        errorMessage = optStringOrNull("errorMessage"),
        sizeBytes = getLong("sizeBytes"),
    )

    private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name)) null else getString(name)

    private companion object {
        const val TAG = "FileEditJournal"
        const val DIRECTORY = "edit-journal"
        const val JSON_SUFFIX = ".json"
    }
}
