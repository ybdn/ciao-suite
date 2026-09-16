package dev.ybdn.ciaocloud.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.model.PhoneMedia
import dev.ybdn.ciaocloud.domain.repository.ExternalMediaResolver
import dev.ybdn.ciaocloud.domain.util.MediaFileTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ContentExternalMediaResolver(
    private val context: Context,
) : ExternalMediaResolver {

    override suspend fun resolve(uris: List<String>, mimeTypeHint: String?): List<PhoneMedia> = withContext(Dispatchers.IO) {
        uris.mapIndexed { index, value ->
            val uri = Uri.parse(value)
            val row = runCatching { queryRow(uri) }.getOrNull().orEmpty()
            val name = row[MediaStore.MediaColumns.DISPLAY_NAME] as? String
                ?: row[OpenableColumns.DISPLAY_NAME] as? String
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "media"
            val mimeType = row[MediaStore.MediaColumns.MIME_TYPE] as? String
                ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()
                ?: MediaFileTypes.fromFileName(name)?.mimeType
                ?: mimeTypeHint
                ?: "image/*"
            val fileModified = if (uri.scheme == "file") uri.path?.let { File(it).lastModified() } else null
            val dateModified = (row[MediaStore.MediaColumns.DATE_MODIFIED] as? Long)?.times(1000) ?: fileModified ?: 0L
            PhoneMedia(
                mediaStoreId = mediaStoreIdOf(value) ?: -(index + 1L),
                uri = value,
                displayName = name,
                mediaType = if (mimeType.startsWith("video/")) MediaType.VIDEO else MediaType.PHOTO,
                mimeType = mimeType,
                sizeBytes = row[OpenableColumns.SIZE] as? Long ?: row[MediaStore.MediaColumns.SIZE] as? Long ?: 0L,
                takenAtEpochMillis = (row[MediaStore.MediaColumns.DATE_TAKEN] as? Long)?.takeIf { it > 0 }
                    ?: dateModified.takeIf { it > 0 }
                    ?: System.currentTimeMillis(),
                dateModifiedEpochMillis = dateModified,
                width = (row[MediaStore.MediaColumns.WIDTH] as? Long)?.toInt() ?: 0,
                height = (row[MediaStore.MediaColumns.HEIGHT] as? Long)?.toInt() ?: 0,
                durationMillis = row[MediaStore.MediaColumns.DURATION] as? Long,
                relativePath = row[MediaStore.MediaColumns.RELATIVE_PATH] as? String,
            )
        }
    }

    override fun mediaStoreIdOf(uri: String): Long? {
        val parsed = Uri.parse(uri)
        if (parsed.authority != MediaStore.AUTHORITY) return null
        // content://media/<volume>/<images|video|file>/media/<id> : volume interne uniquement.
        val volume = parsed.pathSegments.firstOrNull()
        if (volume != MediaStore.VOLUME_EXTERNAL && volume != MediaStore.VOLUME_EXTERNAL_PRIMARY) return null
        return runCatching { ContentUris.parseId(parsed) }.getOrNull()?.takeIf { it >= 0 }
    }

    /** Toutes les colonnes disponibles parmi celles utiles : les providers tiers n'en exposent que quelques-unes. */
    private fun queryRow(uri: Uri): Map<String, Any?>? {
        if (uri.scheme != "content") return null
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            USEFUL_COLUMNS.mapNotNull { column ->
                val index = cursor.getColumnIndex(column)
                if (index < 0 || cursor.isNull(index)) return@mapNotNull null
                column to when (cursor.getType(index)) {
                    android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                    android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                    else -> null
                }
            }.toMap()
        }
    }

    private companion object {
        val USEFUL_COLUMNS = listOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
    }
}
