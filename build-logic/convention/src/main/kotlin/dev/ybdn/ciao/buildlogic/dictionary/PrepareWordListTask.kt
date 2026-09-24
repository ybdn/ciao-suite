package dev.ybdn.ciao.buildlogic.dictionary

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import kotlin.math.min

/**
 * Prépare la liste de mots d'une langue à partir de corpus de la
 * [Leipzig Corpora Collection](https://wortschatz.uni-leipzig.de/) (CC BY 4.0). Tâche manuelle,
 * lancée seulement pour mettre à jour le dictionnaire : les corpus (plusieurs centaines de Mo) ne
 * sont pas versionnés, la liste produite l'est, et le build la compile ensuite
 * ([CompileDictionaryTask]).
 *
 * [corporaDirectory] contient un dossier par corpus, avec ses fichiers `<corpus>-words.txt`
 * (`id`, `mot`, `occurrences`) et `<corpus>-co_n.txt` (voisins immédiats : `id gauche`,
 * `id droit`, `occurrences`, `significativité`).
 *
 * - Les jetons collés par une élision (`l'école`, `qu'il`) sont découpés : `l'` et `école` sont
 *   comptés chacun, et `l'` → `école` compte comme un bigramme.
 * - Les variantes de casse d'un même mot sont fusionnées : « Le » (début de phrase) rejoint « le »,
 *   mais « Paris » reste distinct de « paris », bien plus rare.
 * - Les fautes courantes des corpus web sont écartées ([findMisspellings]), comme les élisions
 *   sans apostrophe (`cest`, `jai`) : sinon le clavier les tiendrait pour des mots connus et ne
 *   les corrigerait pas.
 * - Seuls les mots faits de lettres (avec apostrophes ou traits d'union internes) sont gardés,
 *   les [maxWords] plus fréquents.
 */
abstract class PrepareWordListTask : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    abstract val corporaDirectory: DirectoryProperty

    @get:Input
    abstract val maxWords: Property<Int>

    @get:OutputFile
    abstract val wordsFile: RegularFileProperty

    @get:OutputFile
    abstract val bigramsFile: RegularFileProperty

    init {
        // Outil de maintenance : toujours relancé quand on le demande.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun prepare() {
        val root = corporaDirectory.orNull?.asFile
            ?: throw GradleException("Indiquer le dossier des corpus Leipzig : -Pleipzig=/chemin/vers/corpus")
        val corpora = root.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }
            .map { dir -> Corpus(dir.name, dir.resolve("${dir.name}-words.txt"), dir.resolve("${dir.name}-co_n.txt")) }
            .filter { it.words.isFile && it.neighbours.isFile }
        if (corpora.isEmpty()) throw GradleException("Aucun corpus Leipzig (<corpus>/<corpus>-words.txt) dans $root")

        // 1. Occurrences de chaque mot, tous corpus confondus.
        val counts = HashMap<String, Long>()
        // Mots à élision recollés sans apostrophe : « c'est » → « cest ».
        val elidedForms = HashMap<String, Long>()
        for (corpus in corpora) {
            corpus.forEachWord { _, parts, count ->
                parts.forEach { counts.merge(it, count, Long::plus) }
                if (parts.size >= 2) elidedForms.merge((parts[0].dropLast(1) + parts[1]).lowercase(), count, Long::plus)
            }
        }
        val canonical = mergeCaseVariants(counts)
        val misspellings = findMisspellings(counts) +
            counts.filter { (word, count) -> (elidedForms[word.lowercase()] ?: 0L) >= count * ElisionTypoRatio }.keys
        val kept = counts.entries
            .filter { it.key !in misspellings }
            .filter { it.value >= MinWordCount }
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(maxWords.get())
        val index = HashMap<String, Int>(kept.size * 2)
        kept.forEachIndexed { i, entry -> index[entry.key] = i }
        fun indexOf(word: String): Int = index[canonical[word] ?: word] ?: -1

        // 2. Bigrammes : voisins immédiats des corpus, plus l'intérieur des jetons à élision.
        val pairs = HashMap<Long, Long>()
        fun addPair(left: Int, right: Int, count: Long) {
            if (left >= 0 && right >= 0 && left != right) pairs.merge(left.toLong() shl 32 or right.toLong(), count, Long::plus)
        }
        for (corpus in corpora) {
            val firstWord = HashMap<Int, Int>()
            val lastWord = HashMap<Int, Int>()
            corpus.forEachWord { id, parts, count ->
                val indexes = parts.map(::indexOf)
                firstWord[id] = indexes.first()
                lastWord[id] = indexes.last()
                for (i in 0 until indexes.lastIndex) addPair(indexes[i], indexes[i + 1], count)
            }
            corpus.neighbours.forEachLine { line ->
                val fields = line.split('\t')
                if (fields.size < 3) return@forEachLine
                val left = lastWord[fields[0].toIntOrNull()] ?: return@forEachLine
                val right = firstWord[fields[1].toIntOrNull()] ?: return@forEachLine
                addPair(left, right, fields[2].toLongOrNull() ?: return@forEachLine)
            }
        }
        val nextWords = HashMap<Int, MutableList<Pair<Int, Long>>>()
        for ((key, count) in pairs) {
            if (count < MinBigramCount) continue
            nextWords.getOrPut((key ushr 32).toInt()) { mutableListOf() } += (key and 0xFFFFFFFFL).toInt() to count
        }

        val source = corpora.joinToString(", ") { it.name }
        wordsFile.get().asFile.printWriter().use { out ->
            out.println("# Mots et nombre d'occurrences. Généré par PrepareWordListTask (build-logic), ne pas modifier à la main.")
            out.println("# Source : Leipzig Corpora Collection ($source), CC BY 4.0.")
            kept.forEach { out.println("${it.key}\t${it.value}") }
        }
        bigramsFile.get().asFile.printWriter().use { out ->
            out.println("# Mot, mot suivant et nombre d'occurrences. Généré par PrepareWordListTask (build-logic), ne pas modifier à la main.")
            out.println("# Source : Leipzig Corpora Collection ($source), CC BY 4.0.")
            for (left in kept.indices) {
                val next = nextWords[left] ?: continue
                next.sortWith(compareByDescending<Pair<Int, Long>> { it.second }.thenBy { kept[it.first].key })
                for (i in 0 until min(BigramsPerWord, next.size)) {
                    out.println("${kept[left].key}\t${kept[next[i].first].key}\t${next[i].second}")
                }
            }
        }
        logger.lifecycle("${kept.size} mots, ${nextWords.size} mots avec bigrammes, depuis ${corpora.size} corpus")
    }

    private class Corpus(val name: String, val words: File, val neighbours: File) {
        /** Chaque jeton utilisable du corpus, découpé en mots du dictionnaire. */
        fun forEachWord(action: (id: Int, parts: List<String>, count: Long) -> Unit) {
            words.forEachLine { line ->
                val fields = line.split('\t')
                if (fields.size < 3) return@forEachLine
                val id = fields[0].toIntOrNull() ?: return@forEachLine
                val count = fields[2].toLongOrNull() ?: return@forEachLine
                val parts = splitToken(fields[1]) ?: return@forEachLine
                action(id, parts, count)
            }
        }
    }

    internal companion object {
        /** En dessous, un mot est trop souvent une coquille ou un nom de passage. */
        const val MinWordCount = 3L
        const val MinBigramCount = 5L
        const val BigramsPerWord = 3
        const val MaxWordLength = 32

        /**
         * Élisions du français : même liste que `FrenchTypography` dans l'app (le clavier cherche
         * le mot qui suit seul, le dictionnaire doit donc le contenir seul).
         */
        val ElidedPrefixes = setOf("jusqu", "lorsqu", "puisqu", "quoiqu", "qu", "l", "d", "j", "m", "n", "s", "t", "c")

        /** Lettres de l'alphabet latin seulement : les corpus contiennent aussi du cyrillique, du grec… */
        private val WordPattern = Regex("^\\p{IsLatin}+(?:['-]\\p{IsLatin}+)*$")

        /** `l'école` → [`l'`, `école`] ; null si le jeton n'est pas un mot (chiffres, ponctuation…). */
        fun splitToken(raw: String): List<String>? {
            var rest = raw.replace('’', '\'')
            // Certains corpus détachent l'apostrophe (« l ’ école ») : « l » seul est une élision.
            if (rest.lowercase() in ElidedPrefixes) return listOf(rest.lowercase() + "'")
            val parts = mutableListOf<String>()
            while (true) {
                val apostrophe = rest.indexOf('\'')
                if (apostrophe <= 0) break
                val prefix = rest.substring(0, apostrophe)
                if (prefix.lowercase() !in ElidedPrefixes) break
                parts += prefix.lowercase() + "'"
                rest = rest.substring(apostrophe + 1)
            }
            if (rest.isEmpty()) return parts.ifEmpty { null }
            if (rest.length > MaxWordLength || !WordPattern.matches(rest)) return null
            parts += rest
            return parts
        }

        /**
         * Fusionne les variantes de casse de chaque mot : la forme en minuscules absorbe celles
         * qui ne sont pas plus de [ProperNounRatio] fois plus fréquentes qu'elle (« Bonjour », en
         * tête de phrase), puis la variante en majuscules la plus fréquente absorbe les autres.
         * Renvoie, pour chaque variante absorbée, la forme qui l'a absorbée.
         */
        fun mergeCaseVariants(counts: HashMap<String, Long>): Map<String, String> {
            val canonical = HashMap<String, String>()
            val groups = counts.keys.groupBy { it.lowercase() }
            for ((lower, variants) in groups) {
                if (variants.size < 2) continue
                val byCount = variants.sortedWith(compareByDescending<String> { counts.getValue(it) }.thenBy { it })
                val lowerCount = counts[lower]
                val merges = mutableListOf<Pair<String, String>>()
                val remaining = mutableListOf<String>()
                for (variant in byCount) {
                    if (variant == lower) continue
                    if (lowerCount != null && counts.getValue(variant) <= lowerCount * ProperNounRatio) {
                        merges += variant to lower
                    } else {
                        remaining += variant
                    }
                }
                remaining.drop(1).forEach { merges += it to remaining.first() }
                for ((variant, target) in merges) {
                    canonical[variant] = target
                    counts.merge(target, counts.remove(variant) ?: 0L, Long::plus)
                }
            }
            return canonical
        }

        /**
         * Fautes probables : un mot sans ses accents, son apostrophe ou son trait d'union
         * (`etre`, `ca`, `aujourdhui`) quand la forme correcte est [AccentTypoRatio] fois plus
         * fréquente ; avec un accent en trop (`merçi`) quand elle l'est [ExtraAccentTypoRatio]
         * fois (`sûr` reste, bien plus rare que `sur` mais pas à ce point) ; un mot à qui il
         * manque une lettre ou dont deux lettres sont inversées (`beacoup`) quand la forme
         * correcte est [LetterTypoRatio] fois plus fréquente.
         */
        fun findMisspellings(counts: Map<String, Long>): Set<String> {
            val misspellings = HashSet<String>()
            val byBareForm = counts.keys.groupBy(::bareForm)
            for (group in byBareForm.values) {
                if (group.size < 2) continue
                for (word in group) {
                    val count = counts.getValue(word)
                    val misspelled = group.any { other ->
                        other != word && when {
                            marks(other) > marks(word) -> counts.getValue(other) >= count * AccentTypoRatio
                            // Accent en trop (`merçi`) : seulement très rare, pour garder `sûr`, `dû`.
                            marks(other) < marks(word) -> counts.getValue(other) >= count * ExtraAccentTypoRatio
                            else -> false
                        }
                    }
                    if (misspelled) misspellings += word
                }
            }
            // Morceaux d'un mot à apostrophe que certains corpus coupent en trois jetons
            // (« aujourd ’ hui ») : « aujourd » et « hui » ne sont pas des mots.
            val pieces = HashMap<String, Long>()
            for ((word, count) in counts) {
                val apostrophe = word.indexOf('\'')
                if (apostrophe <= 0 || apostrophe == word.lastIndex) continue
                pieces.merge(word.substring(0, apostrophe), count, ::maxOf)
                pieces.merge(word.substring(apostrophe + 1), count, ::maxOf)
            }
            for ((word, count) in counts) {
                if ((pieces[word] ?: 0L) >= count) misspellings += word
            }
            // Mots à qui manque une lettre : on indexe chaque mot par ses formes à une lettre en moins.
            val deletions = HashMap<String, Long>()
            for ((word, count) in counts) {
                if (word.length < 5) continue
                for (i in word.indices) deletions.merge(word.removeRange(i, i + 1), count, ::maxOf)
            }
            for ((word, count) in counts) {
                if (word.length < 4) continue
                val missingLetter = deletions[word] ?: 0L
                val swapped = (0 until word.length - 1).maxOfOrNull { i ->
                    val chars = word.toCharArray()
                    chars[i] = word[i + 1]
                    chars[i + 1] = word[i]
                    counts[String(chars)] ?: 0L
                } ?: 0L
                if (maxOf(missingLetter, swapped) >= count * LetterTypoRatio) misspellings += word
            }
            return misspellings
        }

        /** Minuscules sans accents, apostrophes ni traits d'union. */
        private fun bareForm(word: String): String =
            java.text.Normalizer.normalize(word.lowercase(), java.text.Normalizer.Form.NFD)
                .filter { it.isLetter() && Character.getType(it) != Character.NON_SPACING_MARK.toInt() }

        /** Nombre d'accents, apostrophes et traits d'union. */
        private fun marks(word: String): Int =
            java.text.Normalizer.normalize(word, java.text.Normalizer.Form.NFD)
                .count { !it.isLetter() || Character.getType(it) == Character.NON_SPACING_MARK.toInt() }

        const val ProperNounRatio = 10
        const val AccentTypoRatio = 10
        const val ExtraAccentTypoRatio = 300
        const val LetterTypoRatio = 100
        const val ElisionTypoRatio = 30
    }
}
