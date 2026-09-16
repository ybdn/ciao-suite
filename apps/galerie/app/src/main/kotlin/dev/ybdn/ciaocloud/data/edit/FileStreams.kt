package dev.ybdn.ciaocloud.data.edit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import dev.ybdn.ciaocloud.domain.model.FileFingerprint
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32

internal const val STREAM_BUFFER_SIZE = 1024 * 1024

/** Lit le flux jusqu'au bout : taille et CRC32 au format des enregistrements de transfert. */
internal fun InputStream.fingerprint(): FileFingerprint {
    val crc32 = CRC32()
    var size = 0L
    val buffer = ByteArray(STREAM_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        crc32.update(buffer, 0, read)
        size += read
    }
    return FileFingerprint(size, crc32.value.toString(16).padStart(8, '0'))
}

/** Copie le flux et renvoie l'empreinte des octets copiés. */
internal fun InputStream.copyWithFingerprint(output: OutputStream): FileFingerprint {
    val crc32 = CRC32()
    var size = 0L
    val buffer = ByteArray(STREAM_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        output.write(buffer, 0, read)
        crc32.update(buffer, 0, read)
        size += read
    }
    return FileFingerprint(size, crc32.value.toString(16).padStart(8, '0'))
}

/**
 * Flux de l'original d'un média. Pour une URI MediaStore, `setRequireOriginal` (avec
 * `ACCESS_MEDIA_LOCATION`) évite le flux expurgé de sa position GPS : l'empreinte est celle du
 * fichier réel, comme lors du transfert.
 */
internal fun Context.openOriginal(uri: Uri): InputStream {
    val resolver = contentResolver
    if (uri.authority == MediaStore.AUTHORITY &&
        checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
    ) {
        runCatching { resolver.openInputStream(MediaStore.setRequireOriginal(uri)) }.getOrNull()?.let { return it }
    }
    return resolver.openInputStream(uri) ?: throw IOException("Impossible d'ouvrir $uri")
}
