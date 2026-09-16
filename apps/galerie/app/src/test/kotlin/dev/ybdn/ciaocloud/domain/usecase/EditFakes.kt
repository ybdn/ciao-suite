package dev.ybdn.ciaocloud.domain.usecase

import dev.ybdn.ciaocloud.domain.model.FileFingerprint
import dev.ybdn.ciaocloud.domain.repository.EditJournal
import dev.ybdn.ciaocloud.domain.repository.EditJournalEntry
import dev.ybdn.ciaocloud.domain.repository.EditWorkspace
import dev.ybdn.ciaocloud.domain.repository.MediaWriteAccess
import dev.ybdn.ciaocloud.domain.repository.MetadataWriter
import dev.ybdn.ciaocloud.domain.repository.PhoneMediaWriter
import dev.ybdn.ciaocloud.domain.repository.SsdMediaWriter
import dev.ybdn.ciaocloud.domain.util.ExifWritePlan
import java.io.IOException
import java.util.zip.CRC32

fun fingerprintOf(bytes: ByteArray): FileFingerprint =
    FileFingerprint(bytes.size.toLong(), CRC32().apply { update(bytes) }.value.toString(16).padStart(8, '0'))

/** Fichiers de travail en mémoire ; « l'orientation » est le dernier octet du fichier. */
class FakeEditWorkspace(private val sources: Map<String, ByteArray>) : EditWorkspace, MetadataWriter {
    val files = HashMap<String, ByteArray>()
    private var counter = 0

    override suspend fun newWorkFile(extension: String) = "work/${counter++}.$extension"
    override suspend fun copyOriginal(sourceUri: String, path: String) {
        files[path] = sources[sourceUri]?.copyOf() ?: throw IOException("source absente")
    }
    override suspend fun fingerprint(path: String) = files[path]?.let(::fingerprintOf)
    override suspend fun delete(path: String) {
        files.remove(path)
    }
    override suspend fun readOrientation(path: String) = files.getValue(path).last().toInt()
    override suspend fun apply(path: String, plan: ExifWritePlan) {
        val bytes = files.getValue(path)
        bytes[bytes.size - 1] = plan.set.getValue("Orientation").toByte()
    }
}

class FakeWriteAccess(var accept: Boolean = true) : MediaWriteAccess {
    val requests = mutableListOf<List<String>>()
    override suspend fun request(uris: List<String>) = accept.also { requests += uris }
}

class FakePhoneWriter(private val work: Map<String, ByteArray>) : PhoneMediaWriter {
    val files = HashMap<String, ByteArray>()
    val pending = HashSet<String>()
    val backups = HashMap<String, ByteArray>()
    val names = HashMap<String, MutableSet<String>>()
    var failReplace = false
    var failRestore = false
    private var nextId = 5000L

    override suspend fun fingerprint(uri: String) = files[uri]?.let(::fingerprintOf)
    override suspend fun existingNames(relativePath: String): Set<String> = names[relativePath].orEmpty()
    override suspend fun createPending(relativePath: String, displayName: String, mimeType: String, dateTakenEpochMillis: Long?): String {
        val uri = "content://media/external/images/media/${nextId++}"
        pending += uri
        files[uri] = ByteArray(0)
        names.getOrPut(relativePath) { HashSet() } += displayName
        return uri
    }
    override suspend fun writePending(uri: String, workPath: String, expected: FileFingerprint) {
        files[uri] = work.getValue(workPath).copyOf()
    }
    override suspend fun publish(uri: String): Long {
        pending -= uri
        return uri.substringAfterLast('/').toLong()
    }
    override suspend fun deleteIfPending(uri: String) {
        if (pending.remove(uri)) files.remove(uri)
    }
    override suspend fun replace(uri: String, workPath: String, expected: FileFingerprint, backupPath: String) {
        backups[backupPath] = files.getValue(uri).copyOf()
        if (failReplace) {
            files[uri] = byteArrayOf(0x66)
            if (failRestore) throw IOException("écriture et restauration impossibles")
            files[uri] = backups.remove(backupPath)!!
            throw IOException("relecture différente")
        }
        files[uri] = work.getValue(workPath).copyOf()
        backups.remove(backupPath)
    }
    override suspend fun backupExists(backupPath: String) = backupPath in backups
    override suspend fun restoreFromBackup(uri: String, backupPath: String) {
        if (failRestore) throw IOException("restauration impossible")
        files[uri] = backups.getValue(backupPath).copyOf()
    }
    override suspend fun deleteBackup(backupPath: String) {
        backups.remove(backupPath)
    }
}

class FakeSsdWriter(private val work: Map<String, ByteArray>) : SsdMediaWriter {
    val files = HashMap<String, ByteArray>()
    var available = true
    var failWrite = false
    /** Noms cibles dont le renommage échoue (toujours). */
    val failRenameTo = HashSet<String>()
    /** Noms cibles dont le prochain renommage échoue. */
    val failRenameOnceTo = HashSet<String>()

    override suspend fun isAvailable() = available
    override suspend fun listNames(relativeDir: String): List<String>? =
        if (!available) null else files.keys.filter { it.substringBeforeLast('/') == relativeDir }.map { it.substringAfterLast('/') }
    override suspend fun fingerprint(relativePath: String) = if (available) files[relativePath]?.let(::fingerprintOf) else null
    override suspend fun exists(relativePath: String) = relativePath in files
    override suspend fun write(relativePath: String, workPath: String, expected: FileFingerprint) {
        if (failWrite) throw IOException("SSD plein")
        check(relativePath !in files) { "écrasement interdit : $relativePath" }
        files[relativePath] = work.getValue(workPath).copyOf()
    }
    override suspend fun rename(relativePath: String, newName: String) {
        if (newName in failRenameTo || failRenameOnceTo.remove(newName)) throw IOException("renommage refusé")
        val target = relativePath.substringBeforeLast('/') + "/" + newName
        check(target !in files) { "renommage vers un nom existant : $target" }
        files[target] = files.remove(relativePath) ?: throw IOException("absent : $relativePath")
    }
    override suspend fun delete(relativePath: String): Boolean {
        files.remove(relativePath)
        return true
    }
}

class FakeEditJournal : EditJournal {
    val entries = LinkedHashMap<String, EditJournalEntry>()
    val written = mutableListOf<EditJournalEntry>()
    override suspend fun write(entry: EditJournalEntry) {
        entries[entry.id] = entry
        written += entry
    }
    override suspend fun remove(id: String) {
        entries.remove(id)
    }
    override suspend fun entries() = entries.values.toList()
    override fun backupPath(id: String) = "backup/$id"
}
