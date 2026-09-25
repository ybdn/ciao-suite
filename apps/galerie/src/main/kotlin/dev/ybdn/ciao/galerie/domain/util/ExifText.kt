package dev.ybdn.ciao.galerie.domain.util

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Texte d'une balise EXIF ASCII. La norme n'autorise que l'ASCII, mais l'app (et la plupart des
 * appareils récents) y écrit de l'UTF-8 pour les accents : décodage UTF-8, à défaut Latin-1.
 */
object ExifText {

    fun decode(bytes: ByteArray?): String? {
        if (bytes == null) return null
        var end = bytes.size
        while (end > 0 && bytes[end - 1] == 0.toByte()) end--
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, 0, end))
                .toString()
        } catch (e: CharacterCodingException) {
            String(bytes, 0, end, Charsets.ISO_8859_1)
        }
        return text.trim().ifEmpty { null }
    }
}
