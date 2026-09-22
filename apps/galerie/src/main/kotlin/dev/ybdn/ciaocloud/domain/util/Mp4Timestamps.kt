package dev.ybdn.ciaocloud.domain.util

/** Accès aléatoire en lecture/écriture à un fichier, abstrait pour tester la logique sans disque. */
interface RandomAccessBytes {
    val length: Long

    /** Lit jusqu'à `buffer.size` octets à [position] ; renvoie le nombre d'octets lus. */
    fun read(position: Long, buffer: ByteArray): Int

    fun write(position: Long, bytes: ByteArray)
}

/**
 * `MediaMuxer` écrit l'heure courante comme date de création et de modification des boîtes `mvhd`,
 * `tkhd` et `mdhd` d'un MP4/3GP. Une copie partagée sans métadonnées ne doit porter aucune date :
 * ces champs sont remis à zéro (1904-01-01, origine de l'horloge MP4).
 */
object Mp4Timestamps {

    private val CONTAINERS = setOf("moov", "trak", "mdia")
    private val TIMED_BOXES = setOf("mvhd", "tkhd", "mdhd")
    private const val HEADER_SIZE = 8L

    /** @return le nombre de boîtes dont les dates ont été effacées. */
    fun erase(file: RandomAccessBytes): Int = eraseIn(file, 0, file.length)

    private fun eraseIn(file: RandomAccessBytes, start: Long, end: Long): Int {
        var erased = 0
        var position = start
        val header = ByteArray(HEADER_SIZE.toInt())
        while (position + HEADER_SIZE <= end) {
            if (file.read(position, header) < HEADER_SIZE) break
            val declaredSize = header.uInt32(0)
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            var headerSize = HEADER_SIZE
            val size = when (declaredSize) {
                0L -> end - position
                1L -> {
                    val large = ByteArray(8)
                    if (file.read(position + HEADER_SIZE, large) < 8) break
                    headerSize += 8
                    large.uInt64(0)
                }
                else -> declaredSize
            }
            if (size < headerSize || position + size > end) break
            val payload = position + headerSize
            when (type) {
                in CONTAINERS -> erased += eraseIn(file, payload, position + size)
                in TIMED_BOXES -> if (eraseTimes(file, payload, position + size)) erased++
            }
            position += size
        }
        return erased
    }

    /** Boîte « full box » : version (1 octet), drapeaux (3), puis création et modification sur 4 (v0) ou 8 octets (v1). */
    private fun eraseTimes(file: RandomAccessBytes, payload: Long, end: Long): Boolean {
        val versionByte = ByteArray(1)
        if (file.read(payload, versionByte) < 1) return false
        val fieldSize = when (versionByte[0].toInt()) {
            0 -> 4
            1 -> 8
            else -> return false
        }
        val timesStart = payload + 4
        if (timesStart + 2L * fieldSize > end) return false
        file.write(timesStart, ByteArray(2 * fieldSize))
        return true
    }

    private fun ByteArray.uInt32(offset: Int): Long =
        (0 until 4).fold(0L) { value, i -> (value shl 8) or (this[offset + i].toLong() and 0xFF) }

    private fun ByteArray.uInt64(offset: Int): Long =
        (0 until 8).fold(0L) { value, i -> (value shl 8) or (this[offset + i].toLong() and 0xFF) }
}
