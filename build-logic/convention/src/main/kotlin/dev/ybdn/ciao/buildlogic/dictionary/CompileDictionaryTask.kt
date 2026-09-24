package dev.ybdn.ciao.buildlogic.dictionary

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Compile une liste de mots ([PrepareWordListTask]) en dictionnaire binaire embarqué dans l'APK :
 * un arbre de préfixes (trie) à plat, que l'app charge d'un bloc sans rien analyser.
 *
 * Format (gros-boutiste), lu par `Dictionary.read` dans l'app `apps/clavier` :
 * ```
 * int      magic "CDIC", int version (1)
 * int      N nombre de nœuds ; le nœud 0 est la racine, les enfants de chaque nœud sont contigus
 * char[N]  caractère de chaque nœud
 * int[N]   indice du premier enfant
 * byte[N]  nombre d'enfants (non signé)
 * byte[N]  fréquence du mot qui finit sur ce nœud, 1..255 (échelle logarithmique), 0 sinon
 * byte[N]  fréquence maximale dans le sous-arbre (élagage de la recherche)
 * int      B nombre de mots suivis de bigrammes
 * int[B]   nœud final de chacun de ces mots (croissant)
 * int[3B]  nœuds finaux des mots suivants les plus fréquents, -1 si moins de trois
 * ```
 */
@CacheableTask
abstract class CompileDictionaryTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val wordsFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bigramsFile: RegularFileProperty

    /** Chemin du fichier produit, relatif au dossier des assets (`dictionary/fr.dict`). */
    @get:Input
    abstract val assetPath: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun compile() {
        val counts = LinkedHashMap<String, Long>()
        readTsv(wordsFile.get().asFile) { fields -> counts[fields[0]] = fields[1].toLong() }
        val maxLog = ln(counts.values.max().toDouble()).coerceAtLeast(1.0)

        val root = Node('\u0000')
        val ends = HashMap<String, Node>(counts.size * 2)
        for ((word, count) in counts) {
            var node = root
            for (char in word) node = node.children.getOrPut(char) { Node(char) }
            node.frequency = 1 + (254 * ln(count.toDouble()) / maxLog).roundToInt().coerceIn(0, 254)
            ends[word] = node
        }
        root.computeMaxFrequency()

        // Parcours en largeur : les enfants de chaque nœud reçoivent des indices contigus.
        val nodes = ArrayList<Node>()
        nodes += root
        var head = 0
        while (head < nodes.size) {
            val node = nodes[head++]
            node.firstChild = nodes.size
            node.sortedChildren = node.children.values.sortedBy { it.char }
            nodes += node.sortedChildren
        }
        nodes.forEachIndexed { i, node -> node.index = i }

        val nextWords = sortedMapOf<Int, MutableList<Int>>()
        readTsv(bigramsFile.get().asFile) { fields ->
            val left = ends[fields[0]] ?: return@readTsv
            val right = ends[fields[1]] ?: return@readTsv
            val next = nextWords.getOrPut(left.index) { mutableListOf() }
            if (next.size < BigramsPerWord) next += right.index
        }

        val output = outputDirectory.get().asFile.resolve(assetPath.get())
        output.parentFile.mkdirs()
        DataOutputStream(BufferedOutputStream(output.outputStream())).use { out ->
            out.writeInt(Magic)
            out.writeInt(Version)
            out.writeInt(nodes.size)
            nodes.forEach { out.writeChar(it.char.code) }
            nodes.forEach { out.writeInt(it.firstChild) }
            nodes.forEach { out.writeByte(it.sortedChildren.size.also { size -> check(size < 256) }) }
            nodes.forEach { out.writeByte(it.frequency) }
            nodes.forEach { out.writeByte(it.maxFrequency) }
            out.writeInt(nextWords.size)
            nextWords.keys.forEach(out::writeInt)
            nextWords.values.forEach { next -> repeat(BigramsPerWord) { out.writeInt(next.getOrElse(it) { -1 }) } }
        }
        logger.info("${counts.size} mots, ${nodes.size} nœuds, ${nextWords.size} mots avec bigrammes → $output")
    }

    private fun readTsv(file: File, action: (List<String>) -> Unit) {
        file.forEachLine { line -> if (line.isNotEmpty() && !line.startsWith("#")) action(line.split('\t')) }
    }

    private class Node(val char: Char) {
        val children = HashMap<Char, Node>()
        var sortedChildren: List<Node> = emptyList()
        var frequency = 0
        var maxFrequency = 0
        var firstChild = 0
        var index = 0

        fun computeMaxFrequency(): Int {
            maxFrequency = maxOf(frequency, children.values.maxOfOrNull { it.computeMaxFrequency() } ?: 0)
            return maxFrequency
        }
    }

    private companion object {
        const val Magic = 0x43444943 // "CDIC"
        const val Version = 1
        const val BigramsPerWord = 3
    }
}
