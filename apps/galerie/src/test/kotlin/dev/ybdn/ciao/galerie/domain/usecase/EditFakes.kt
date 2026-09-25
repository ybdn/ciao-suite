package dev.ybdn.ciao.galerie.domain.usecase

import dev.ybdn.ciao.galerie.domain.model.EditRecipe
import dev.ybdn.ciao.galerie.domain.model.FileFingerprint
import dev.ybdn.ciao.galerie.domain.repository.DestinationEntry
import dev.ybdn.ciao.galerie.domain.repository.EncodedFormat
import dev.ybdn.ciao.galerie.domain.repository.PhotoEditRenderer
import dev.ybdn.ciao.galerie.domain.repository.RenderedImage
import dev.ybdn.ciao.galerie.domain.repository.EditJournal
import dev.ybdn.ciao.galerie.domain.repository.EditJournalEntry
import dev.ybdn.ciao.galerie.domain.repository.EditWorkspace
import dev.ybdn.ciao.galerie.domain.repository.MediaWriteAccess
import dev.ybdn.ciao.galerie.domain.repository.MetadataWriter
import dev.ybdn.ciao.galerie.domain.repository.PhoneMediaWriter
import dev.ybdn.ciao.galerie.domain.repository.SsdMediaWriter
import dev.ybdn.ciao.galerie.domain.util.ExifWritePlan
import java.io.IOException
import java.util.zip.CRC32

fun fingerprintOf(bytes: ByteArray): FileFingerprint =
    FileFingerprint(bytes.size.toLong(), CRC32().apply { update(bytes) }.value.toString(16).padStart(8, '0'))

/** Fichiers de travail en mémoire ; « l'orientation » est le dernier octet du fichier. */
class FakeEditWorkspace(private val sources: Map<String, ByteArray>) : EditWorkspace, MetadataWriter {
    val files = HashMap<String, ByteArray>()
    /** Balises EXIF simulées par fichier source, recopiées dans les fichiers de travail. */
    val sourceTags = HashMap<String, Map<String, String>>()
    private val tags = HashMap<String, MutableMap<String, String>>()
    private var counter = 0

    override suspend fun newWorkFile(extension: String) = "work/${counter++}.$extension"
    override suspend fun copyOriginal(sourceUri: String, path: String) {
        files[path] = sources[sourceUri]?.copyOf() ?: throw IOException("source absente")
        tags[path] = sourceTags[sourceUri].orEmpty().toMutableMap()
    }
    override suspend fun readTags(path: String, tags: List<String>) = this.tags[path].orEmpty().filterKeys { it in tags }
    override suspend fun fingerprint(path: String) = files[path]?.let(::fingerprintOf)
    override suspend fun delete(path: String) {
        files.remove(path)
    }
    val copiedTags = mutableListOf<Pair<String, List<String>>>()
    override suspend fun copyTags(sourceUri: String, path: String, tags: List<String>) {
        copiedTags += sourceUri to tags
    }
    override suspend fun readOrientation(path: String) = files.getValue(path).last().toInt()
    val appliedPlans = mutableListOf<ExifWritePlan>()
    override suspend fun apply(path: String, plan: ExifWritePlan) {
        appliedPlans += plan
        tags.getOrPut(path) { HashMap() }.apply {
            putAll(plan.set)
            plan.remove.forEach { remove(it) }
        }
        val bytes = files.getValue(path)
        val orientation = plan.set["Orientation"]
        if (orientation != null) {
            bytes[bytes.size - 1] = orientation.toByte()
        } else {
            // Autres balises : contenu modifié, orientation (dernier octet) intacte.
            files[path] = byteArrayOf(plan.hashCode().toByte()) + bytes
        }
    }
}

/** Rendu factice : octets de la source suivis d'un octet propre à la recette. */
class FakePhotoRenderer(private val sources: Map<String, ByteArray>, private val files: MutableMap<String, ByteArray>) : PhotoEditRenderer {
    val rendered = mutableListOf<Pair<EditRecipe, EncodedFormat>>()
    override suspend fun render(sourceUri: String, recipe: EditRecipe, format: EncodedFormat, outputPath: String): RenderedImage {
        rendered += recipe to format
        files[outputPath] = sources.getValue(sourceUri) + byteArrayOf(recipe.hashCode().toByte(), 42)
        return RenderedImage(640, 480)
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
    /** Dossiers existants (casse d'origine). */
    val directories = HashSet<String>()
    var failMove = false
    override suspend fun resolveDirectory(relativeDir: String, create: Boolean): String? {
        val existing = (directories + files.keys.map { it.substringBeforeLast('/') })
            .firstOrNull { it.equals(relativeDir, ignoreCase = true) }
        return existing ?: relativeDir.takeIf { create }?.also { directories += it }
    }
    override suspend fun listEntries(relativeDir: String): List<DestinationEntry>? =
        files.filterKeys { it.substringBeforeLast('/') == relativeDir }
            .map { (path, bytes) -> DestinationEntry(path.substringAfterLast('/'), bytes.size.toLong(), false) }
    override suspend fun sameContent(firstPath: String, secondPath: String) =
        files[firstPath]?.contentEquals(files[secondPath]) == true
    override suspend fun move(fromPath: String, toDir: String, newName: String) {
        if (failMove) throw IOException("déplacement refusé")
        val target = "$toDir/$newName"
        check(target !in files) { "écrasement interdit : $target" }
        files[target] = files.remove(fromPath) ?: throw IOException("absent : $fromPath")
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
