package dev.ybdn.ciaocloud.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.os.Bundle
import android.provider.MediaStore
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.model.PhoneMedia
import dev.ybdn.ciaocloud.domain.repository.TrashedMedia
import dev.ybdn.ciaocloud.domain.repository.TrashedMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

/** Photos et vidéos du volume interne présentes dans la corbeille système (`IS_TRASHED`). */
class MediaStoreTrashedSource(
    private val context: Context,
) : TrashedMediaSource {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTrashed(): Flow<List<TrashedMedia>> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(MediaStoreCollections.FILES, true, observer)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }
        .onStart { emit(Unit) }
        .mapLatest { query() }
        .flowOn(Dispatchers.IO)

    private fun query(): List<TrashedMedia> {
        val args = Bundle().apply {
            putString(
                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (" +
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})",
            )
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putStringArray(
                android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.MediaColumns.DATE_EXPIRES),
            )
        }
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATE_EXPIRES,
        )
        val cursor = context.contentResolver.query(MediaStoreCollections.FILES, projection, args, null)
            ?: return emptyList()
        return cursor.use {
            val result = ArrayList<TrashedMedia>(it.count)
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val isVideo = it.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val collection = if (isVideo) MediaStoreCollections.VIDEO else MediaStoreCollections.IMAGES
                val dateModified = it.getLong(6) * 1000L
                result += TrashedMedia(
                    media = PhoneMedia(
                        mediaStoreId = id,
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        displayName = it.getString(2) ?: "media_$id",
                        mediaType = if (isVideo) MediaType.VIDEO else MediaType.PHOTO,
                        mimeType = it.getString(3) ?: "application/octet-stream",
                        sizeBytes = it.getLong(4),
                        takenAtEpochMillis = it.getLong(5).takeIf { millis -> millis > 0 } ?: dateModified,
                        dateModifiedEpochMillis = dateModified,
                        width = 0,
                        height = 0,
                        durationMillis = if (isVideo && !it.isNull(7)) it.getLong(7) else null,
                        relativePath = it.getString(8),
                    ),
                    expiresAtEpochMillis = it.getLong(9) * 1000L,
                )
            }
            result
        }
    }
}
