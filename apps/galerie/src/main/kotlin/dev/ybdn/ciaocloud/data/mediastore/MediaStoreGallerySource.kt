package dev.ybdn.ciaocloud.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.model.PhoneMedia
import dev.ybdn.ciaocloud.domain.repository.PhoneGallerySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * Médias du volume interne pour la galerie, lus en une requête sur les colonnes légères
 * uniquement (pas d'EXIF ni de métadonnées vidéo : la grille doit s'afficher en moins d'une seconde).
 * Relu à chaque notification MediaStore, regroupées pour absorber les rafales (transfert, prise de vue).
 */
class MediaStoreGallerySource(
    private val context: Context,
) : PhoneGallerySource {

    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override fun observeMedia(): Flow<List<PhoneMedia>> =
        merge(mediaStoreChanges(), refreshRequests)
            .debounce(CHANGE_DEBOUNCE_MS)
            .onStart { emit(Unit) }
            .mapLatest { queryMedia() }
            .flowOn(Dispatchers.IO)

    override fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    private fun mediaStoreChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(MediaStoreCollections.IMAGES, true, observer)
        resolver.registerContentObserver(MediaStoreCollections.VIDEO, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    private fun queryMedia(): List<PhoneMedia> {
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (" +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
        val cursor = context.contentResolver.query(
            MediaStoreCollections.FILES,
            PROJECTION,
            selection,
            null,
            "${MediaStore.MediaColumns.DATE_TAKEN} DESC",
        ) ?: return emptyList()

        return cursor.use {
            val result = ArrayList<PhoneMedia>(it.count)
            while (it.moveToNext()) {
                val id = it.getLong(COL_ID)
                val isVideo = it.getInt(COL_MEDIA_TYPE) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val collection: Uri = if (isVideo) MediaStoreCollections.VIDEO else MediaStoreCollections.IMAGES
                val dateModifiedMillis = it.getLong(COL_DATE_MODIFIED) * 1000L
                result += PhoneMedia(
                    mediaStoreId = id,
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    displayName = it.getString(COL_DISPLAY_NAME) ?: "media_$id",
                    mediaType = if (isVideo) MediaType.VIDEO else MediaType.PHOTO,
                    mimeType = it.getString(COL_MIME_TYPE) ?: "application/octet-stream",
                    sizeBytes = it.getLong(COL_SIZE),
                    takenAtEpochMillis = it.getLong(COL_DATE_TAKEN).takeIf { millis -> millis > 0 } ?: dateModifiedMillis,
                    dateModifiedEpochMillis = dateModifiedMillis,
                    width = it.getInt(COL_WIDTH),
                    height = it.getInt(COL_HEIGHT),
                    durationMillis = if (isVideo && !it.isNull(COL_DURATION)) it.getLong(COL_DURATION) else null,
                    relativePath = it.getString(COL_RELATIVE_PATH),
                )
            }
            result
        }
    }

    private companion object {
        const val CHANGE_DEBOUNCE_MS = 500L

        val PROJECTION = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        const val COL_ID = 0
        const val COL_MEDIA_TYPE = 1
        const val COL_DISPLAY_NAME = 2
        const val COL_MIME_TYPE = 3
        const val COL_SIZE = 4
        const val COL_DATE_TAKEN = 5
        const val COL_DATE_MODIFIED = 6
        const val COL_WIDTH = 7
        const val COL_HEIGHT = 8
        const val COL_DURATION = 9
        const val COL_RELATIVE_PATH = 10
    }
}
