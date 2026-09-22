package dev.ybdn.ciaocloud.data.mediastore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import dev.ybdn.ciaocloud.domain.model.MediaDetails
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.repository.MediaDetailsReader
import dev.ybdn.ciaocloud.domain.util.ExifText
import dev.ybdn.ciaocloud.domain.util.Iso6709
import dev.ybdn.ciaocloud.domain.util.MediaMetadataCodes
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
        fun string(tag: String) = exif.getAttribute(tag)?.trim()?.trimEnd('\u0000')?.trim()?.ifEmpty { null }
        // Textes libres : UTF-8 (écrit par l'app et la plupart des appareils) plutôt que l'ASCII d'ExifInterface.
        fun text(tag: String) = ExifText.decode(exif.getAttributeBytes(tag))
        fun int(tag: String) = exif.getAttributeInt(tag, -1).takeIf { it >= 0 }
        fun positiveDouble(tag: String) = exif.getAttributeDouble(tag, 0.0).takeIf { it > 0 && it.isFinite() }
        val latLong = exif.latLong
        return MediaDetails(
            captureLocalDateTime = string(ExifInterface.TAG_DATETIME_ORIGINAL)?.let {
                runCatching { LocalDateTime.parse(it.take(19), EXIF_DATE_FORMATTER) }.getOrNull()
            },
            captureUtcOffsetMinutes = string(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)?.let {
                runCatching { ZoneOffset.of(it).totalSeconds / 60 }.getOrNull()
            },
            width = int(ExifInterface.TAG_IMAGE_WIDTH)?.takeIf { it > 0 }
                ?: int(ExifInterface.TAG_PIXEL_X_DIMENSION)?.takeIf { it > 0 },
            height = int(ExifInterface.TAG_IMAGE_LENGTH)?.takeIf { it > 0 }
                ?: int(ExifInterface.TAG_PIXEL_Y_DIMENSION)?.takeIf { it > 0 },
            cameraMake = string(ExifInterface.TAG_MAKE),
            cameraModel = string(ExifInterface.TAG_MODEL),
            lensMake = string(ExifInterface.TAG_LENS_MAKE),
            lensModel = string(ExifInterface.TAG_LENS_MODEL),
            iso = int(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.takeIf { it > 0 },
            fNumber = positiveDouble(ExifInterface.TAG_F_NUMBER),
            exposureTimeSeconds = positiveDouble(ExifInterface.TAG_EXPOSURE_TIME),
            focalLengthMm = positiveDouble(ExifInterface.TAG_FOCAL_LENGTH),
            focalLength35mm = int(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)?.takeIf { it > 0 },
            exposureBiasEv = exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE)?.let {
                exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, Double.NaN).takeIf { it.isFinite() }
            },
            exposureProgram = MediaMetadataCodes.exposureProgram(int(ExifInterface.TAG_EXPOSURE_PROGRAM)),
            meteringMode = MediaMetadataCodes.meteringMode(int(ExifInterface.TAG_METERING_MODE)),
            flashFired = MediaMetadataCodes.flashFired(int(ExifInterface.TAG_FLASH)),
            whiteBalanceManual = MediaMetadataCodes.whiteBalanceManual(int(ExifInterface.TAG_WHITE_BALANCE)),
            sceneType = MediaMetadataCodes.sceneType(int(ExifInterface.TAG_SCENE_CAPTURE_TYPE)),
            digitalZoomRatio = positiveDouble(ExifInterface.TAG_DIGITAL_ZOOM_RATIO)?.takeIf { it > 1.0 },
            // 0 = distance inconnue, 0xFFFFFFFF = infini.
            subjectDistanceMeters = positiveDouble(ExifInterface.TAG_SUBJECT_DISTANCE)?.takeIf { it < 100_000 },
            rotationDegrees = exif.rotationDegrees,
            software = text(ExifInterface.TAG_SOFTWARE),
            artist = text(ExifInterface.TAG_ARTIST),
            copyright = text(ExifInterface.TAG_COPYRIGHT),
            description = text(ExifInterface.TAG_IMAGE_DESCRIPTION),
            latitude = latLong?.get(0),
            longitude = latLong?.get(1),
            altitudeMeters = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE)?.let {
                exif.getAltitude(Double.NaN).takeIf { it.isFinite() }
            },
            isMotionPhoto = exif.getAttributeBytes(ExifInterface.TAG_XMP)?.toString(Charsets.UTF_8)
                ?.let { xmp -> MOTION_PHOTO_MARKERS.any { it in xmp } } ?: false,
            directionDegrees = exif.getAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION)?.let {
                exif.getAttributeDouble(ExifInterface.TAG_GPS_IMG_DIRECTION, Double.NaN).takeIf { it in 0.0..360.0 }
            },
        )
    }

    private fun readVideo(uri: Uri): MediaDetails {
        val retriever = MediaMetadataRetriever()
        val details = try {
            retriever.setDataSource(context, uri)
            fun string(key: Int) = retriever.extractMetadata(key)?.trim()?.ifEmpty { null }
            fun int(key: Int) = string(key)?.toIntOrNull()
            val rotation = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: 0
            val rawWidth = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val rawHeight = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotated = rotation == 90 || rotation == 270
            val location = Iso6709.parse(string(MediaMetadataRetriever.METADATA_KEY_LOCATION))
            val durationMillis = string(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            MediaDetails(
                width = if (rotated) rawHeight else rawWidth,
                height = if (rotated) rawWidth else rawHeight,
                durationMillis = durationMillis,
                latitude = location?.first,
                longitude = location?.second,
                containerMimeType = string(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                hasAudio = string(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)?.let { it == "yes" },
                frameRate = MediaMetadataCodes.frameRate(int(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT), durationMillis),
                bitrateBitsPerSecond = string(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.takeIf { it > 0 },
                hdrFormat = MediaMetadataCodes.hdrFormat(int(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)),
            )
        } finally {
            retriever.release()
        }
        return runCatching { withTrackInfo(details, uri) }.getOrDefault(details)
    }

    /** Codecs et cadence déclarée des pistes, que `MediaMetadataRetriever` n'expose pas. */
    private fun withTrackInfo(details: MediaDetails, uri: Uri): MediaDetails {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val formats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
            val video = formats.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            val audio = formats.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            fun MediaFormat.number(key: String) = if (containsKey(key)) runCatching { getNumber(key) }.getOrNull() else null
            return details.copy(
                videoCodecMimeType = video?.getString(MediaFormat.KEY_MIME),
                audioCodecMimeType = audio?.getString(MediaFormat.KEY_MIME),
                hasAudio = details.hasAudio ?: (audio != null),
                audioSampleRateHz = audio?.number(MediaFormat.KEY_SAMPLE_RATE)?.toInt()?.takeIf { it > 0 },
                audioChannels = audio?.number(MediaFormat.KEY_CHANNEL_COUNT)?.toInt()?.takeIf { it > 0 },
                frameRate = video?.number(MediaFormat.KEY_FRAME_RATE)?.toDouble()?.takeIf { it in 1.0..1000.0 }
                    ?: details.frameRate,
            )
        } finally {
            extractor.release()
        }
    }

    private companion object {
        val MOTION_PHOTO_MARKERS = listOf("MotionPhoto", "MicroVideo")

        val EXIF_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", Locale.US).withResolverStyle(ResolverStyle.STRICT)
    }
}
