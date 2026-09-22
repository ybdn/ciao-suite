package dev.ybdn.ciaocloud.domain.util

/** Lecture minimale d'un en-tête WebP (RIFF) pour savoir si l'image est compressée sans perte. */
object WebpHeader {

    /**
     * true si le premier bloc d'image est `VP8L` (sans perte), false s'il est `VP8 ` (avec perte), si un
     * canal alpha séparé (`ALPH`, propre au format avec perte) le précède, ou si l'en-tête est illisible.
     * [header] : début du fichier, assez long pour atteindre le bloc d'image.
     */
    fun isLossless(header: ByteArray): Boolean {
        if (header.size < 16 || header.fourCc(0) != "RIFF" || header.fourCc(8) != "WEBP") return false
        var position = 12
        while (position + 8 <= header.size) {
            when (header.fourCc(position)) {
                "VP8L" -> return true
                "VP8 ", "ALPH" -> return false
            }
            val size = header.uInt32LittleEndian(position + 4)
            // Les blocs RIFF de taille impaire sont suivis d'un octet de bourrage.
            val next = position + 8L + size + (size and 1L)
            if (next > Int.MAX_VALUE) return false
            position = next.toInt()
        }
        return false
    }

    private fun ByteArray.fourCc(offset: Int): String = String(this, offset, 4, Charsets.ISO_8859_1)

    private fun ByteArray.uInt32LittleEndian(offset: Int): Long =
        (3 downTo 0).fold(0L) { value, i -> (value shl 8) or (this[offset + i].toLong() and 0xFF) }
}
