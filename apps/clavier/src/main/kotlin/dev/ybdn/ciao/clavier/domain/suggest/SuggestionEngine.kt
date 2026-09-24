package dev.ybdn.ciao.clavier.domain.suggest

import dev.ybdn.ciao.clavier.domain.input.FrenchTypography

/** Propositions pour un mot tapé, la meilleure en premier. */
data class Suggestions(
    val typed: String,
    val words: List<String>,
    /** Mot qui remplacera [typed] à l'espace ou à la ponctuation, si la confiance suffit (§7.1). */
    val autocorrection: String?,
)

/**
 * Moteur de suggestions (apps/clavier/docs/spec-v1.md §7.1) : complétion (`bonj` → `bonjour`),
 * correction des fautes de frappe selon la proximité des touches AZERTY (`bonjoir` → `bonjour`)
 * et restitution des accents (`ecole` → `école`).
 *
 * Parcours en profondeur de l'arbre du [Dictionary] avec une distance d'édition pondérée
 * ([TypingCosts]) calculée rangée par rangée : chaque nœud prolonge la rangée de son parent, et
 * une branche est abandonnée dès que plus aucun mot n'y peut être assez proche ou assez fréquent.
 *
 * Les mots appris ([personal], §7.3) sont cherchés de la même façon dans leur propre petit arbre ;
 * les mots embarqués souvent choisis dans la barre reçoivent un bonus.
 *
 * Travaille sur un mot seul : l'élision (`l'`) et la casse sont gérées par l'appelant.
 */
class SuggestionEngine(
    private val dictionary: Dictionary,
    private val personal: PersonalLexicon = PersonalLexicon.Empty,
) {

    /**
     * Propositions pour [typed], au plus [limit]. [previousWord] (le mot tapé juste avant)
     * favorise les mots qui le suivent souvent.
     */
    fun suggest(typed: String, previousWord: String? = null, limit: Int = 3): Suggestions {
        if (typed.isEmpty() || typed.length > MaxTypedLength) return Suggestions(typed, emptyList(), null)
        val context = previousWord?.let(::findAnyCase)?.let(dictionary::nextWords).orEmpty()
        val learned = personal.learned?.let { Search(it, typed, emptyList(), emptyMap()).run() }.orEmpty()
        // Le mot tapé aux accents près passe avant toute autre correction : `ile` → `île`, pas `le`.
        val ranked = (Search(dictionary, typed, context, personal.boosts).run() + learned).map { it.ranked() }
            .plus(elisionCandidates(typed))
            .sortedWith(compareBy<Ranked> { !it.isAccentVariant }.thenByDescending { it.score })
        // Un mot connu passe en tête : c'est ce que l'utilisateur a voulu taper.
        val known = knownWord(typed)
        val words = (listOfNotNull(known) + ranked.map { it.word }).distinct().take(limit)
        return Suggestions(typed, words, if (known == null) autocorrection(typed, ranked) else null)
    }

    /** Prédiction du mot suivant, sans rien de tapé (§7.2, bigrammes). */
    fun predict(previousWord: String, limit: Int = 3): List<String> {
        val node = findAnyCase(previousWord) ?: return emptyList()
        return dictionary.nextWords(node).take(limit).map(dictionary::word)
    }

    /**
     * [word] est dans le dictionnaire : tel quel, ou en minuscules quand il est tapé avec une
     * majuscule (début de phrase, verrouillage). Un mot connu n'est jamais corrigé.
     */
    fun isKnown(word: String): Boolean = knownWord(word) != null

    /** [word] est dans le dictionnaire embarqué, et pas seulement parmi les mots appris. */
    fun isInDictionary(word: String): Boolean = findAnyCase(word) != null

    /** Le même moteur avec un autre dictionnaire personnel. */
    fun withPersonal(personal: PersonalLexicon) = SuggestionEngine(dictionary, personal)

    /** Forme connue de [word] : dictionnaire embarqué, puis mots appris. */
    private fun knownWord(word: String): String? =
        findAnyCase(word)?.let(dictionary::word)
            ?: personal.learned?.let { learned -> learned.findAnyCase(word).takeIf { it >= 0 }?.let(learned::word) }

    private fun findAnyCase(word: String): Int? = dictionary.findAnyCase(word).takeIf { it >= 0 }

    /**
     * Apostrophe d'élision oubliée : `jai` → `j'ai`, `cest` → `c'est`, `quil` → `qu'il`. Le
     * dictionnaire range le mot élidé à part (`j'` puis `ai`) : on cherche le reste seul, accents
     * compris (`jetais` → `j'étais`).
     */
    private fun elisionCandidates(typed: String): List<Ranked> {
        if (typed.any { it == '\'' || it == '’' }) return emptyList()
        return FrenchTypography.ElidedPrefixes.flatMap { prefix ->
            if (typed.length <= prefix.length || !typed.startsWith(prefix, ignoreCase = true)) return@flatMap emptyList()
            val rest = typed.substring(prefix.length)
            if (TypingCosts.fold(rest[0]) !in ElisionVowels || (rest.length == 1 && rest.lowercase() !in ShortElided)) {
                return@flatMap emptyList()
            }
            val elision = dictionary.find("$prefix'")
            val context = if (elision >= 0) dictionary.nextWords(elision) else emptyList()
            Search(dictionary, rest, context, emptyMap()).run()
                // Un reste très court n'est pris que s'il suit souvent l'élision : `na` → `n'a`,
                // mais pas `ca` → `c'a` ni `mon` → `m'on`.
                .filter { !it.completion && it.cost <= TypingCosts.WrongAccent && (rest.length > 2 || it.node in context) }
                .map {
                    val cost = it.cost + TypingCosts.ElisionOmission
                    Ranked("$prefix'${dictionary.word(it.node)}", cost, completion = false, it.score - CostWeight * TypingCosts.ElisionOmission)
                }
        }
    }

    private fun autocorrection(typed: String, ranked: List<Ranked>): String? {
        if (typed.length < 2) return null
        val best = ranked.firstOrNull() ?: return null
        if (best.completion || best.cost > autocorrectMaxCost(typed.length)) return null
        // Jamais un sigle à la place d'un mot tapé en minuscules : `mrc` ne devient pas `MRC`.
        if (best.word.count { it.isUpperCase() } >= 2 && typed.none { it.isUpperCase() }) return null
        // Deux corrections presque aussi probables : mieux vaut laisser choisir.
        val second = ranked.drop(1).firstOrNull { !it.word.equals(best.word, ignoreCase = true) }
        if (best.cost > TypingCosts.WrongAccent && second != null && best.score - second.score < AutocorrectMargin) return null
        return best.word
    }

    private class Ranked(val word: String, val cost: Float, val completion: Boolean, val score: Float) {
        val isAccentVariant: Boolean get() = !completion && cost <= TypingCosts.WrongAccent
    }

    private class Candidate(
        val dictionary: Dictionary,
        val node: Int,
        val cost: Float,
        val completion: Boolean,
        val score: Float,
    ) {
        fun ranked() = Ranked(dictionary.word(node), cost, completion, score)
    }

    /**
     * Une recherche : l'état du parcours de [dictionary] pour un mot tapé. [boosts] : bonus de
     * score par nœud (mots souvent choisis dans la barre).
     */
    private class Search(
        private val dictionary: Dictionary,
        typed: String,
        private val context: List<Int>,
        private val boosts: Map<Int, Int>,
    ) {
        private val raw = typed.toCharArray()
        private val folded = CharArray(raw.size) { TypingCosts.fold(raw[it]) }
        private val n = raw.size
        private val maxCost = maxCost(n)
        private val maxCompletionCost = maxCompletionCost(n)
        private val contextBonus = if (context.isEmpty()) 0f else ContextBonus
        private val maxBoost = if (boosts.isEmpty()) 0f else PersonalDictionaryRules.MaxBoost.toFloat()

        /** Une rangée de distance par profondeur : rows[d][j] = coût du préfixe de d lettres contre les j premières lettres tapées. */
        private val rows = Array(n + MaxExtraDepth + 2) { FloatArray(n + 1) }
        private val best = ArrayList<Candidate>(Kept + 1)

        fun run(): List<Candidate> {
            val root = rows[0]
            root[0] = 0f
            for (j in 1..n) root[j] = root[j - 1] + TypingCosts.extra(raw, j - 1)
            visitChildren(Dictionary.Root, depth = 0, completionBase = NoCompletion)
            return best
        }

        private fun visitChildren(node: Int, depth: Int, completionBase: Float) {
            if (depth + 1 >= rows.size) return
            val parent = rows[depth]
            val row = rows[depth + 1]
            val grandparent = if (depth >= 1) rows[depth - 1] else null
            val parentChar = if (depth >= 1) TypingCosts.fold(dictionary.char(node)) else '\u0000'
            val first = dictionary.firstChild(node)
            for (child in first until first + dictionary.childCount(node)) {
                val char = dictionary.char(child)
                val foldedChar = TypingCosts.fold(char)
                val omission = TypingCosts.omission(char, doubled = foldedChar == parentChar)
                row[0] = parent[0] + omission
                var rowMin = row[0]
                for (j in 1..n) {
                    var cost = parent[j - 1] + TypingCosts.substitution(raw[j - 1], char)
                    cost = minOf(cost, parent[j] + omission)
                    cost = minOf(cost, row[j - 1] + TypingCosts.extra(raw, j - 1))
                    if (grandparent != null && j >= 2 && folded[j - 1] == parentChar && folded[j - 2] == foldedChar &&
                        folded[j - 1] != folded[j - 2]
                    ) {
                        cost = minOf(cost, grandparent[j - 2] + TypingCosts.Transposition)
                    }
                    if (j >= 2 && isLigature(char, folded[j - 2], folded[j - 1])) {
                        cost = minOf(cost, parent[j - 2] + TypingCosts.Ligature)
                    }
                    row[j] = cost
                    if (cost < rowMin) rowMin = cost
                }

                val full = row[n]
                val base = if (full <= maxCompletionCost) minOf(completionBase, full) else completionBase
                if (rowMin > maxCost && base == NoCompletion) continue

                // Meilleur score encore possible dans ce sous-arbre : inutile d'y descendre s'il ne
                // peut pas entrer dans les meilleurs candidats.
                val lowestCost = minOf(rowMin, base + CompletionPenalty + CompletionLetterPenalty)
                if (best.size >= Kept && score(dictionary.maxFrequency(child), lowestCost) + contextBonus + maxBoost <= best.last().score) continue

                val frequency = dictionary.frequency(child)
                if (frequency > 0) {
                    val completionCost = base + CompletionPenalty + CompletionLetterPenalty * maxOf(1, depth + 1 - n)
                    if (full <= maxCost && full <= completionCost) {
                        offer(child, full, completion = false, frequency)
                    } else if (base != NoCompletion) {
                        offer(child, completionCost, completion = true, frequency)
                    }
                }
                visitChildren(child, depth + 1, base)
            }
        }

        private fun offer(node: Int, cost: Float, completion: Boolean, frequency: Int) {
            val score = score(frequency, cost) + (if (node in context) contextBonus else 0f) + (boosts[node] ?: 0)
            if (best.size >= Kept && score <= best.last().score) return
            val index = best.indexOfFirst { it.score < score }.let { if (it < 0) best.size else it }
            best.add(index, Candidate(dictionary, node, cost, completion, score))
            if (best.size > Kept) best.removeAt(best.lastIndex)
        }
    }

    private companion object {
        /** Au-delà, ce n'est plus un mot (adresse, suite de lettres) : pas de recherche. */
        const val MaxTypedLength = 32

        /** Profondeur de l'arbre au-delà du mot tapé : lettres oubliées et complétions. */
        const val MaxExtraDepth = 20

        /** Candidats gardés pendant le parcours (plus que les 3 affichés, pour trier et dédoublonner). */
        const val Kept = 8

        /** Valeur d'une faute, en unités de fréquence (échelle logarithmique 1..255 du dictionnaire). */
        const val CostWeight = 90f

        /** Un mot complété vaut un peu moins qu'un mot tapé en entier. */
        const val CompletionPenalty = 0.15f

        /** Et d'autant moins qu'il reste de lettres à ajouter : `ca` → `ça` plutôt que `cas`. */
        const val CompletionLetterPenalty = 0.05f

        /** Bonus d'un mot qui suit souvent le mot précédent (bigramme). */
        const val ContextBonus = 12f

        /** Écart de score minimal avec la deuxième proposition pour corriger une vraie faute. */
        const val AutocorrectMargin = 6f

        const val NoCompletion = Float.MAX_VALUE / 4

        fun score(frequency: Int, cost: Float): Float = frequency - CostWeight * cost

        /** Écart toléré selon la longueur tapée : un mot court n'admet guère que des accents. */
        fun maxCost(length: Int): Float = when {
            length <= 1 -> 0.35f
            length == 2 -> 0.7f
            length <= 4 -> 1.1f
            length <= 7 -> 1.8f
            else -> 2.4f
        }

        /** Écart toléré sur le début d'un mot pour le compléter. */
        fun maxCompletionCost(length: Int): Float = when {
            length <= 2 -> 0.2f
            length <= 4 -> 0.7f
            else -> 1.1f
        }

        /** Écart toléré pour corriger sans demander. */
        fun autocorrectMaxCost(length: Int): Float = when {
            length <= 2 -> 0.35f
            length <= 4 -> 1f
            else -> 1.8f
        }

        /** Après une élision, le mot commence par une voyelle ou un h muet. */
        const val ElisionVowels = "aeiouyhœæ"

        /** Mots d'une lettre après une élision : `n'a`, `j'y`, `l'a`. */
        val ShortElided = setOf("a", "y", "à")

        fun isLigature(word: Char, first: Char, second: Char): Boolean = when (word.lowercaseChar()) {
            'œ' -> first == 'o' && second == 'e'
            'æ' -> first == 'a' && second == 'e'
            else -> false
        }
    }
}
