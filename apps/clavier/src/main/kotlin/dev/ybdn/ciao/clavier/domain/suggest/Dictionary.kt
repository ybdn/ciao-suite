package dev.ybdn.ciao.clavier.domain.suggest

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Dictionnaire embarqué (apps/clavier/docs/spec-v1.md §7.2) : arbre de préfixes à plat, compilé
 * au build par `CompileDictionaryTask` (build-logic, qui documente le format) et chargé d'un bloc.
 *
 * Un mot est désigné par l'indice de son dernier nœud. Les enfants d'un nœud sont contigus et
 * triés par caractère.
 */
class Dictionary private constructor(
    private val chars: CharArray,
    private val firstChild: IntArray,
    private val childCount: ByteArray,
    private val frequencies: ByteArray,
    private val maxFrequencies: ByteArray,
    private val bigramKeys: IntArray,
    private val bigramTargets: IntArray,
) {
    private val parents = IntArray(chars.size).also { parents ->
        parents[Root] = -1
        for (node in chars.indices) {
            val first = firstChild[node]
            for (child in first until first + childCount(node)) parents[child] = node
        }
    }

    val nodeCount: Int get() = chars.size

    fun char(node: Int): Char = chars[node]

    fun firstChild(node: Int): Int = firstChild[node]

    fun childCount(node: Int): Int = childCount[node].toInt() and 0xFF

    /** Fréquence du mot qui finit sur [node], 1..255 (logarithmique) ; 0 si aucun mot n'y finit. */
    fun frequency(node: Int): Int = frequencies[node].toInt() and 0xFF

    /** Plus grande fréquence d'un mot de ce sous-arbre (le nœud compris). */
    fun maxFrequency(node: Int): Int = maxFrequencies[node].toInt() and 0xFF

    fun isWord(node: Int): Boolean = frequencies[node].toInt() != 0

    /** Le mot qui finit sur [node]. */
    fun word(node: Int): String {
        val builder = StringBuilder()
        var current = node
        while (current != Root) {
            builder.append(chars[current])
            current = parents[current]
        }
        return builder.reverse().toString()
    }

    /** Nœud final de [word] s'il est dans le dictionnaire, tel quel (casse et accents compris), sinon -1. */
    fun find(word: String): Int {
        var node = Root
        for (char in word) {
            node = child(node, char)
            if (node < 0) return -1
        }
        return if (isWord(node)) node else -1
    }

    /** Mots qui suivent le plus souvent le mot [node], du plus au moins fréquent. */
    fun nextWords(node: Int): List<Int> {
        val index = bigramKeys.binarySearch(node)
        if (index < 0) return emptyList()
        return (0 until BigramsPerWord).map { bigramTargets[index * BigramsPerWord + it] }.filter { it >= 0 }
    }

    private fun child(node: Int, char: Char): Int {
        var low = firstChild[node]
        var high = low + childCount(node) - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val value = chars[middle]
            when {
                value < char -> low = middle + 1
                value > char -> high = middle - 1
                else -> return middle
            }
        }
        return -1
    }

    companion object {
        const val Root = 0
        private const val Magic = 0x43444943 // "CDIC"
        private const val Version = 1
        private const val BigramsPerWord = 3

        /** Lit un dictionnaire compilé : lecture en bloc, sans analyse, pour tenir sous 300 ms (§7.2). */
        fun read(input: InputStream): Dictionary {
            val buffer = ByteBuffer.wrap(input.readBytes())
            if (buffer.getInt() != Magic || buffer.getInt() != Version) throw IOException("Dictionnaire illisible")
            val nodeCount = buffer.getInt()
            val chars = CharArray(nodeCount).also { buffer.asCharBuffer().get(it) }
            buffer.position(buffer.position() + nodeCount * Char.SIZE_BYTES)
            val firstChild = IntArray(nodeCount).also { buffer.asIntBuffer().get(it) }
            buffer.position(buffer.position() + nodeCount * Int.SIZE_BYTES)
            val childCount = ByteArray(nodeCount).also { buffer.get(it) }
            val frequencies = ByteArray(nodeCount).also { buffer.get(it) }
            val maxFrequencies = ByteArray(nodeCount).also { buffer.get(it) }
            val bigramCount = buffer.getInt()
            val bigramKeys = IntArray(bigramCount).also { buffer.asIntBuffer().get(it) }
            buffer.position(buffer.position() + bigramCount * Int.SIZE_BYTES)
            val bigramTargets = IntArray(bigramCount * BigramsPerWord).also { buffer.asIntBuffer().get(it) }
            return Dictionary(chars, firstChild, childCount, frequencies, maxFrequencies, bigramKeys, bigramTargets)
        }
    }
}
