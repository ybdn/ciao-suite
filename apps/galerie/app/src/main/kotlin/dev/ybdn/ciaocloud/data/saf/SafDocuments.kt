package dev.ybdn.ciaocloud.data.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/** Document (fichier ou dossier) listé sous une arborescence SAF. */
data class SafEntry(
    /** URI de document construite sous le tree URI racine (conserve la permission persistée). */
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val lastModifiedMillis: Long,
) {
    val isDirectory: Boolean get() = mimeType == Document.MIME_TYPE_DIR
}

/**
 * Accès bas niveau à une arborescence SAF via `DocumentsContract`. Plus rapide que `DocumentFile`,
 * qui déclenche une requête au provider par attribut lu (nom, taille, type…) de chaque enfant :
 * ici, lister un dossier et obtenir toutes les métadonnées de ses enfants coûte une seule requête.
 */
object SafDocuments {

    private val CHILD_PROJECTION = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_SIZE,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_LAST_MODIFIED,
    )

    /** URI de document du dossier racine d'un tree URI obtenu par `ACTION_OPEN_DOCUMENT_TREE`. */
    fun rootDocumentUri(treeUri: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    fun listChildren(resolver: ContentResolver, directoryUri: Uri): List<SafEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            directoryUri,
            DocumentsContract.getDocumentId(directoryUri),
        )
        val entries = mutableListOf<SafEntry>()
        resolver.query(childrenUri, CHILD_PROJECTION, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(0) ?: continue
                entries += SafEntry(
                    uri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentId),
                    name = cursor.getString(1) ?: continue,
                    sizeBytes = if (cursor.isNull(2)) -1 else cursor.getLong(2),
                    mimeType = cursor.getString(3),
                    lastModifiedMillis = if (cursor.isNull(4)) 0 else cursor.getLong(4),
                )
            }
        }
        return entries
    }

    /** Nom affiché d'un document, ou null s'il n'existe plus (SSD débranché, fichier supprimé). */
    fun displayName(resolver: ContentResolver, documentUri: Uri): String? = runCatching {
        resolver.query(documentUri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    fun createDocument(resolver: ContentResolver, parentUri: Uri, mimeType: String, name: String): Uri? =
        runCatching { DocumentsContract.createDocument(resolver, parentUri, mimeType, name) }.getOrNull()

    fun deleteDocument(resolver: ContentResolver, documentUri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(resolver, documentUri) }.getOrDefault(false)
}
