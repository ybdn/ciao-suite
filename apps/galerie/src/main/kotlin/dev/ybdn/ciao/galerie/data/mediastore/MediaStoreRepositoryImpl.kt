package dev.ybdn.ciao.galerie.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import dev.ybdn.ciao.galerie.domain.model.MediaFile
import dev.ybdn.ciao.galerie.domain.model.MediaType
import dev.ybdn.ciao.galerie.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.TimeZone

/**
 * Scanne les photos (`MediaStore.Images`) et vidéos (`MediaStore.Video`) du stockage interne.
 * Date effective : EXIF `DateTimeOriginal` (+ `OffsetTimeOriginal`) pour les photos, métadonnées `MediaMetadataRetriever`
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
        capturedDateResolver: (Uri) -> CapturedDate?,
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
                    val captured = capturedDateResolver(uri)
                    MediaFile(
                        mediaStoreId = id,
                        uri = uri.toString(),
                        displayName = it.getString(nameCol) ?: "media_$id",
                        mediaType = mediaType,
                        mimeType = it.getString(mimeCol) ?: "application/octet-stream",
                        sizeBytes = it.getLong(sizeCol),
                        capturedAtEpochMillis = captured?.epochMillis,
                        fileDateEpochMillis = dateTaken ?: (it.getLong(dateModifiedCol) * 1000L),
                        captureUtcOffsetMinutes = captured?.utcOffsetMinutes,
                    )
                }
                    .onSuccess(results::add)
                    .onFailure { error -> Log.w(TAG, "Média $id ignoré (illisible)", error) }
            }
        }

        return results
    }

    /**
     * `DateTimeOriginal` est une heure locale du lieu de prise de vue, sans fuseau. Si l'appareil a
     * écrit `OffsetTimeOriginal` (EXIF 2.31, cas des Pixel), l'instant et le décalage sont exacts ;
     * sinon l'heure est interprétée dans le fuseau du téléphone, et le décalage reste inconnu.
     */
    private fun readExifCapturedDate(uri: Uri): CapturedDate? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val localDateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?.let(::parseExifDateTime)
                ?: return@use null
            val offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)?.let(::parseExifOffset)
            val instant = if (offset != null) {
                localDateTime.toInstant(offset)
            } else {
                localDateTime.atZone(ZoneId.systemDefault()).toInstant()
            }
            CapturedDate(instant.toEpochMilli(), offset?.totalSeconds?.div(60))
                .takeIf { it.epochMillis > MIN_PLAUSIBLE_EPOCH_MILLIS }
        }
    }.getOrNull()

    /** L'instant vidéo est en UTC : son décalage est inféré plus tard des photos voisines. */
    private fun readVideoCapturedDate(uri: Uri): CapturedDate? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                ?.let { parseDate(VIDEO_DATE_FORMAT, it) }
                ?.let { CapturedDate(it, utcOffsetMinutes = null) }
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

    /** Les 19 premiers caractères seulement : certains appareils ajoutent des fractions de seconde. */
    private fun parseExifDateTime(value: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(value.trim().take(19), EXIF_DATE_FORMATTER) }.getOrNull()

    /** Format EXIF "+02:00" / "-05:00". */
    private fun parseExifOffset(value: String): ZoneOffset? =
        runCatching { ZoneOffset.of(value.trim()) }.getOrNull()

    private data class CapturedDate(val epochMillis: Long, val utcOffsetMinutes: Int?)

    private companion object {
        const val TAG = "MediaStoreRepository"
        const val SQL_IN_CHUNK_SIZE = 500

        /** 1990-01-01 : toute date antérieure est considérée comme absente. */
        const val MIN_PLAUSIBLE_EPOCH_MILLIS = 631_152_000_000L

        val IMAGES_COLLECTION: Uri = MediaStoreCollections.IMAGES
        val VIDEO_COLLECTION: Uri = MediaStoreCollections.VIDEO

        val EXIF_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", Locale.US).withResolverStyle(ResolverStyle.STRICT)

        val VIDEO_DATE_FORMAT = SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
    }
}
