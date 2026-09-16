package dev.ybdn.ciaocloud.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import dev.ybdn.ciaocloud.domain.model.MediaFile
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Scanne les photos (`MediaStore.Images`) et vidéos (`MediaStore.Video`) du stockage interne.
 * Date effective : EXIF `DateTimeOriginal` pour les photos, métadonnées `MediaMetadataRetriever`
 * pour les vidéos, avec fallback sur la date MediaStore dans les deux cas.
 * Les fichiers illisibles/corrompus sont ignorés (skip + log), sans bloquer le reste du scan.
 */
class MediaStoreRepositoryImpl(
    private val context: Context,
) : MediaRepository {

    override suspend fun scanLocalMedia(excludedRelativePath: String?): List<MediaFile> =
        withContext(Dispatchers.IO) {
            queryMedia(IMAGES_COLLECTION, MediaType.PHOTO, excludedRelativePath, ::readExifCapturedDate) +
                queryMedia(VIDEO_COLLECTION, MediaType.VIDEO, excludedRelativePath, ::readVideoCapturedDate)
        }

    override suspend fun findExistingIds(mediaStoreIds: Collection<Long>): Set<Long> =
        withContext(Dispatchers.IO) {
            val existing = HashSet<Long>()
            for (collection in listOf(IMAGES_COLLECTION, VIDEO_COLLECTION)) {
                for (chunk in mediaStoreIds.chunked(SQL_IN_CHUNK_SIZE)) {
                    context.contentResolver.query(
                        collection,
                        arrayOf(MediaStore.MediaColumns._ID),
                        "${MediaStore.MediaColumns._ID} IN (${chunk.joinToString(",")})",
                        null,
                        null,
                    )?.use { cursor ->
                        while (cursor.moveToNext()) existing.add(cursor.getLong(0))
                    }
                }
            }
            existing
        }

    private fun queryMedia(
        collection: Uri,
        mediaType: MediaType,
        excludedRelativePath: String?,
        capturedDateResolver: (Uri) -> Long?,
    ): List<MediaFile> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )

        val results = mutableListOf<MediaFile>()

        // LIKE est insensible à la casse en SQLite, comme les chemins du stockage partagé.
        val selection = excludedRelativePath?.let {
            // IS NULL : sinon NOT LIKE sur une valeur nulle exclurait aussi ces médias.
            "(${MediaStore.MediaColumns.RELATIVE_PATH} IS NULL OR " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} NOT LIKE ? ESCAPE '\\')"
        }
        val selectionArgs = excludedRelativePath?.let { path ->
            arrayOf(path.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%")
        }

        val cursor = context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        ) ?: return emptyList()

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateTakenCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val dateModifiedCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                runCatching {
                    val uri = ContentUris.withAppendedId(collection, id)
                    val dateTaken = it.getLong(dateTakenCol).takeIf { millis -> millis > 0 }
                    MediaFile(
                        mediaStoreId = id,
                        uri = uri.toString(),
                        displayName = it.getString(nameCol) ?: "media_$id",
                        mediaType = mediaType,
                        mimeType = it.getString(mimeCol) ?: "application/octet-stream",
                        sizeBytes = it.getLong(sizeCol),
                        capturedAtEpochMillis = capturedDateResolver(uri),
                        fileDateEpochMillis = dateTaken ?: (it.getLong(dateModifiedCol) * 1000L),
                    )
                }
                    .onSuccess(results::add)
                    .onFailure { error -> Log.w(TAG, "Média $id ignoré (illisible)", error) }
            }
        }

        return results
    }

    private fun readExifCapturedDate(uri: Uri): Long? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?.let { parseDate(EXIF_DATE_FORMAT, it) }
        }
    }.getOrNull()

    private fun readVideoCapturedDate(uri: Uri): Long? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                ?.let { parseDate(VIDEO_DATE_FORMAT, it) }
        } finally {
            retriever.release()
        }
    }.getOrNull()

    /**
     * Les valeurs "vides" rencontrées en pratique (EXIF "0000:00:00 00:00:00", date vidéo
     * "19040101T000000.000Z" des conteneurs MP4 sans date) sont rejetées pour laisser place au fallback.
     */
    private fun parseDate(format: SimpleDateFormat, value: String): Long? =
        runCatching { synchronized(format) { format.parse(value.trim()) }?.time }
            .getOrNull()
            ?.takeIf { it > MIN_PLAUSIBLE_EPOCH_MILLIS }

    private companion object {
        const val TAG = "MediaStoreRepository"
        const val SQL_IN_CHUNK_SIZE = 500

        /** 1990-01-01 : toute date antérieure est considérée comme absente. */
        const val MIN_PLAUSIBLE_EPOCH_MILLIS = 631_152_000_000L

        // Volume interne uniquement : le SSD branché en USB est lui aussi indexé par MediaStore
        // et ne doit jamais être scanné (ni proposé à la suppression).
        val IMAGES_COLLECTION: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val VIDEO_COLLECTION: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        /** EXIF ne porte pas de fuseau : interprété dans le fuseau du téléphone. */
        val EXIF_DATE_FORMAT = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply { isLenient = false }

        val VIDEO_DATE_FORMAT = SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
    }
}
