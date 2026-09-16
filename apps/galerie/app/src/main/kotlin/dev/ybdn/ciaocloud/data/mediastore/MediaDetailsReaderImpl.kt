package dev.ybdn.ciaocloud.data.mediastore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import dev.ybdn.ciaocloud.domain.model.MediaDetails
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.repository.MediaDetailsReader
import dev.ybdn.ciaocloud.domain.util.Iso6709
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Métadonnées via `ExifInterface` (photos) et `MediaMetadataRetriever` (vidéos), sans réseau. */
class MediaDetailsReaderImpl(
    private val context: Context,
) : MediaDetailsReader {

    override suspend fun read(uri: String, mediaType: MediaType, isMediaStoreUri: Boolean): MediaDetails? =
        withContext(Dispatchers.IO) {
            runCatching {
                when (mediaType) {
                    MediaType.PHOTO -> openStream(Uri.parse(uri), isMediaStoreUri)?.use(::readExif)
                    MediaType.VIDEO -> readVideo(Uri.parse(uri))
                }
            }.getOrNull()
        }

    /**
     * Les coordonnées GPS ne sont présentes que dans l'original MediaStore (`setRequireOriginal`),
     * qui exige `ACCESS_MEDIA_LOCATION` : sans elle, lecture du flux expurgé.
     */
    private fun openStream(uri: Uri, isMediaStoreUri: Boolean): InputStream? {
        val resolver = context.contentResolver
        if (isMediaStoreUri && context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            runCatching { resolver.openInputStream(MediaStore.setRequireOriginal(uri)) }.getOrNull()?.let { return it }
        }
        return resolver.openInputStream(uri)
    }

    private fun readExif(stream: InputStream): MediaDetails {
        val exif = ExifInterface(stream)
        val latLong = exif.latLong
        return MediaDetails(
            captureLocalDateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let {
                runCatching { LocalDateTime.parse(it.trim().take(19), EXIF_DATE_FORMATTER) }.getOrNull()
            },
            captureUtcOffsetMinutes = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)?.let {
                runCatching { ZoneOffset.of(it.trim()).totalSeconds / 60 }.getOrNull()
            },
            width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
                ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0).takeIf { it > 0 },
            height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
                ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0).takeIf { it > 0 },
            cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.ifEmpty { null },
            cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.ifEmpty { null },
            lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.ifEmpty { null },
            iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 },
            fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 },
            exposureTimeSeconds = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 },
            focalLengthMm = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 },
            latitude = latLong?.get(0),
            longitude = latLong?.get(1),
        )
    }

    private fun readVideo(uri: Uri): MediaDetails {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun int(key: Int) = retriever.extractMetadata(key)?.toIntOrNull()
            val rotation = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: 0
            val rawWidth = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val rawHeight = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotated = rotation == 90 || rotation == 270
            val location = Iso6709.parse(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION))
            return MediaDetails(
                width = if (rotated) rawHeight else rawWidth,
                height = if (rotated) rawWidth else rawHeight,
                durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                latitude = location?.first,
                longitude = location?.second,
            )
        } finally {
            retriever.release()
        }
    }

    private companion object {
        val EXIF_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", Locale.US).withResolverStyle(ResolverStyle.STRICT)
    }
}
