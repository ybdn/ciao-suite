package dev.ybdn.ciaocloud.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
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
 * Scanne les photos (`MediaStore.Images`) et vidéos (`MediaStore.Video`) du stockage local.
 * Date effective : EXIF `DateTimeOriginal` pour les photos, métadonnées `MediaMetadataRetriever`
 * pour les vidéos, avec fallback sur la date de fichier MediaStore dans les deux cas.
 * Les fichiers illisibles/corrompus sont ignorés (skip), sans bloquer le reste du scan.
 */
class MediaStoreRepositoryImpl(
    private val context: Context,
) : MediaRepository {

    override suspend fun scanLocalMedia(): List<MediaFile> = withContext(Dispatchers.IO) {
        scanImages() + scanVideos()
    }

    private fun scanImages(): List<MediaFile> = queryMedia(
        collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        mediaType = MediaType.PHOTO,
    ) { uri, fileDateEpochMillis ->
        readExifCapturedDate(uri) ?: fileDateEpochMillis
    }

    private fun scanVideos(): List<MediaFile> = queryMedia(
        collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        mediaType = MediaType.VIDEO,
    ) { uri, fileDateEpochMillis ->
        readVideoCapturedDate(uri) ?: fileDateEpochMillis
    }

    private inline fun queryMedia(
        collection: Uri,
        mediaType: MediaType,
        capturedDateResolver: (Uri, Long) -> Long?,
    ): List<MediaFile> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )

        val results = mutableListOf<MediaFile>()

        val cursor = context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        ) ?: return emptyList()

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

            while (it.moveToNext()) {
                runCatching {
                    val id = it.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val fileDateEpochMillis = it.getLong(dateCol) * 1000L

                    MediaFile(
                        mediaStoreId = id,
                        uri = uri.toString(),
                        displayName = it.getString(nameCol) ?: "media_$id",
                        mediaType = mediaType,
                        mimeType = it.getString(mimeCol) ?: "application/octet-stream",
                        sizeBytes = it.getLong(sizeCol),
                        capturedAtEpochMillis = capturedDateResolver(uri, fileDateEpochMillis)
                            ?.takeIf { captured -> captured != fileDateEpochMillis },
                        fileDateEpochMillis = fileDateEpochMillis,
                    )
                }.onSuccess { mediaFile -> results.add(mediaFile) }
                // Fichier illisible/corrompu : on l'ignore silencieusement et on continue le scan.
            }
        }

        return results
    }

    private fun readExifCapturedDate(uri: Uri): Long? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val dateString = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: return@use null
            EXIF_DATE_FORMAT.parse(dateString)?.time
        }
    }.getOrNull()

    private fun readVideoCapturedDate(uri: Uri): Long? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            val dateString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                ?: return@use null
            VIDEO_DATE_FORMAT.parse(dateString)?.time
        }
    }.getOrNull()

    private companion object {
        val EXIF_DATE_FORMAT = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)

        val VIDEO_DATE_FORMAT = SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}

private inline fun MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> Long?): Long? =
    try {
        block(this)
    } finally {
        release()
    }
