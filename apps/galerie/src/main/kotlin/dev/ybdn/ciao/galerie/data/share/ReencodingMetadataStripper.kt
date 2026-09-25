package dev.ybdn.ciao.galerie.data.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dev.ybdn.ciao.galerie.domain.repository.MetadataStripper
import dev.ybdn.ciao.galerie.domain.repository.StripFailureReason
import dev.ybdn.ciao.galerie.domain.util.Mp4Timestamps
import dev.ybdn.ciao.galerie.domain.util.RandomAccessBytes
import dev.ybdn.ciao.galerie.domain.util.StripStrategy
import dev.ybdn.ciao.galerie.domain.util.StripTarget
import dev.ybdn.ciao.galerie.domain.util.StrippedMetadataCheck
import dev.ybdn.ciao.galerie.domain.util.VideoContainer
import dev.ybdn.ciao.galerie.domain.util.WebpHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

/**
 * Nettoyage par reconstruction plutôt que par retrait de balises : une photo est décodée (orientation
 * appliquée aux pixels) puis réencodée, ce qui n'emporte ni EXIF, ni XMP, ni IPTC, ni vignette, ni vidéo
 * de photo animée ; le profil ICC et la carte de gain Ultra HDR sont rattachés au bitmap par la
 * plateforme. Une vidéo est remultiplexée : seuls les échantillons des pistes vidéo et audio sont
 * recopiés, sans position ni date. Chaque copie est ensuite relue (spec v3 D5).
 */
class ReencodingMetadataStripper(
    private val context: Context,
) : MetadataStripper {

    private val resolver get() = context.contentResolver

    override suspend fun strip(sourceUri: String, target: StripTarget, outputPath: String): StripFailureReason? =
        withContext(Dispatchers.IO) {
            val output = File(outputPath)
            val failure = try {
                val uri = Uri.parse(sourceUri)
                when (target.strategy) {
                    StripStrategy.REENCODE_JPEG, StripStrategy.CONVERT_TO_JPEG ->
                        reencodePhoto(uri, Bitmap.CompressFormat.JPEG, output)
                    StripStrategy.REENCODE_PNG -> reencodePhoto(uri, Bitmap.CompressFormat.PNG, output)
                    StripStrategy.REENCODE_WEBP -> reencodePhoto(
                        uri,
                        if (isLosslessWebp(uri)) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY,
                        output,
                    )
                    StripStrategy.REMUX_VIDEO -> remuxVideo(uri, target.videoContainer ?: VideoContainer.MP4, output)
                    StripStrategy.UNSUPPORTED -> StripFailureReason.UNSUPPORTED_FORMAT
                }
            } catch (e: CancellationException) {
                output.delete()
                throw e
            } catch (e: FileNotFoundException) {
                StripFailureReason.UNREADABLE_SOURCE
            } catch (e: SecurityException) {
                StripFailureReason.UNREADABLE_SOURCE
            } catch (e: Exception) {
                StripFailureReason.WRITE_FAILED
            }
            if (failure != null) output.delete()
            failure
        }

    private suspend fun reencodePhoto(uri: Uri, format: Bitmap.CompressFormat, output: File): StripFailureReason? {
        val bitmap = try {
            // Décodage pleine résolution : le but est la confidentialité, pas la compression.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: FileNotFoundException) {
            return StripFailureReason.UNREADABLE_SOURCE
        } catch (e: SecurityException) {
            return StripFailureReason.UNREADABLE_SOURCE
        } catch (e: Exception) {
            return StripFailureReason.DECODE_FAILED
        } catch (e: OutOfMemoryError) {
            return StripFailureReason.DECODE_FAILED
        }
        try {
            coroutineContext.ensureActive()
            val written = output.outputStream().use { bitmap.compress(format, QUALITY, it) }
            if (!written) return StripFailureReason.WRITE_FAILED
        } finally {
            bitmap.recycle()
        }
        return verifyPhoto(output)
    }

    private fun verifyPhoto(file: File): StripFailureReason? {
        val exif = ExifInterface(file)
        if (StrippedMetadataCheck.FORBIDDEN_EXIF_TAGS.any { exif.getAttribute(it) != null }) {
            return StripFailureReason.METADATA_REMAINING
        }
        val xmp = exif.getAttributeBytes(ExifInterface.TAG_XMP)?.toString(Charsets.UTF_8)
        return if (StrippedMetadataCheck.disallowedXmpPrefixes(xmp).isEmpty()) null else StripFailureReason.METADATA_REMAINING
    }

    private fun isLosslessWebp(uri: Uri): Boolean = runCatching {
        resolver.openInputStream(uri)?.use { input -> WebpHeader.isLossless(input.readNBytes(WEBP_HEADER_BYTES)) }
    }.getOrNull() ?: false

    private suspend fun remuxVideo(uri: Uri, container: VideoContainer, output: File): StripFailureReason? {
        val extractor = MediaExtractor()
        try {
            try {
                extractor.setDataSource(context, uri, null)
            } catch (e: Exception) {
                return StripFailureReason.UNREADABLE_SOURCE
            }
            val muxer = MediaMuxer(
                output.path,
                when (container) {
                    VideoContainer.MP4 -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                    VideoContainer.THREE_GPP -> MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP
                    VideoContainer.WEBM -> MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                },
            )
            try {
                copySamples(extractor, muxer, container, uri)?.let { return it }
            } finally {
                runCatching { muxer.release() }
            }
        } finally {
            extractor.release()
        }
        if (container != VideoContainer.WEBM) {
            RandomAccessFile(output, "rw").use { Mp4Timestamps.erase(FileBytes(it)) }
        }
        return verifyVideo(output)
    }

    /** Pistes vidéo et audio uniquement : les pistes de métadonnées (capteurs, repères) sont écartées. */
    private suspend fun copySamples(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        container: VideoContainer,
        uri: Uri,
    ): StripFailureReason? {
        val trackMap = HashMap<Int, Int>()
        var bufferSize = MIN_SAMPLE_BUFFER
        var rotation: Int? = null
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
            if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_ROTATION)) {
                rotation = format.getInteger(MediaFormat.KEY_ROTATION)
            }
            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }
            trackMap[index] = try {
                muxer.addTrack(format)
            } catch (e: Exception) {
                return StripFailureReason.TRACK_REJECTED
            }
            extractor.selectTrack(index)
        }
        if (trackMap.isEmpty()) return StripFailureReason.TRACK_REJECTED
        if (container != VideoContainer.WEBM) {
            muxer.setOrientationHint(rotation ?: sourceRotation(uri))
        }

        try {
            muxer.start()
            var buffer = ByteBuffer.allocateDirect(bufferSize)
            val info = MediaCodec.BufferInfo()
            while (true) {
                coroutineContext.ensureActive()
                val size = try {
                    extractor.readSampleData(buffer, 0)
                } catch (e: IllegalArgumentException) {
                    // Échantillon plus grand que le tampon annoncé : on l'agrandit.
                    if (buffer.capacity() >= MAX_SAMPLE_BUFFER) return StripFailureReason.TRACK_REJECTED
                    buffer = ByteBuffer.allocateDirect(buffer.capacity() * 2)
                    continue
                }
                if (size < 0) break
                val track = trackMap[extractor.sampleTrackIndex]
                if (track != null) {
                    val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    info.set(0, size, extractor.sampleTime, flags)
                    muxer.writeSampleData(track, buffer, info)
                }
                extractor.advance()
            }
            muxer.stop()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return StripFailureReason.TRACK_REJECTED
        }
        return null
    }

    private fun sourceRotation(uri: Uri): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        } finally {
            retriever.release()
        }
    }

    private fun verifyVideo(file: File): StripFailureReason? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            return if (location.isNullOrBlank() && !StrippedMetadataCheck.isMeaningfulVideoDate(date)) {
                null
            } else {
                StripFailureReason.METADATA_REMAINING
            }
        } finally {
            retriever.release()
        }
    }

    private companion object {
        const val QUALITY = 95
        const val WEBP_HEADER_BYTES = 256 * 1024
        const val MIN_SAMPLE_BUFFER = 4 * 1024 * 1024
        const val MAX_SAMPLE_BUFFER = 128 * 1024 * 1024
    }
}

private class FileBytes(private val file: RandomAccessFile) : RandomAccessBytes {
    override val length: Long get() = file.length()

    override fun read(position: Long, buffer: ByteArray): Int {
        file.seek(position)
        var total = 0
        while (total < buffer.size) {
            val read = file.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    override fun write(position: Long, bytes: ByteArray) {
        file.seek(position)
        file.write(bytes)
    }
}
