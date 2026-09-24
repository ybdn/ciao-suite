package dev.ybdn.ciao.clavier.domain.suggest

/**
 * Un mot du dictionnaire personnel (apps/clavier/docs/spec-v1.md §7.3), sous sa forme affichée.
 *
 * - [learned] : mot appris (tapé et conservé deux fois, ou ajouté à la main), suggéré mais jamais
 *   corrigé, listé dans les réglages.
 * - sinon, soit un mot inconnu conservé une seule fois (candidat), soit un mot du dictionnaire
 *   embarqué choisi dans la barre ([uses] fois) : invisibles, mais effacés avec le reste.
 */
data class PersonalWord(val word: String, val uses: Int, val learned: Boolean, val lastUsedAt: Long)

/** Règles de l'apprentissage personnel (§7.3), sans stockage. */
object PersonalDictionaryRules {

    /** Un mot inconnu est appris à sa deuxième utilisation conservée. */
    const val LearnAfterUses = 2

    /** Candidats (mots vus une fois) gardés au plus : les plus anciens sont oubliés. */
    const val MaxCandidates = 500

    const val MaxWordLength = 32

    /** Clé d'un mot : sans la casse (« Mdr » en début de phrase et « mdr » sont le même mot). */
    fun key(word: String): String = word.lowercase()

    /**
     * Mot qu'on peut apprendre : des lettres, avec apostrophes ou traits d'union à l'intérieur
     * (`aujourd'hui`, `peut-être`), au moins deux lettres. Jamais un chiffre ni une espace.
     */
    fun isLearnable(word: String): Boolean =
        word.length in 2..MaxWordLength &&
            word.first().isLetter() && word.last().isLetter() &&
            word.all { it.isLetter() || it == '\'' || it == '’' || it == '-' } &&
            word.count { it.isLetter() } >= 2

    /**
     * Mot saisi à la main dans les réglages : espaces autour retirées, apostrophe typographique
     * ramenée à l'apostrophe droite (comme le dictionnaire embarqué). Null s'il n'est pas valable.
     */
    fun normalizeManual(input: String): String? = input.trim().replace('’', '\'').takeIf(::isLearnable)

    /**
     * Forme retenue quand un même mot revient avec une autre casse : la moins capitalisée, car une
     * majuscule peut venir du début de phrase (« Mdr » puis « mdr » → « mdr »), mais un nom tapé
     * toujours avec sa majuscule la garde (« Kévin »).
     */
    fun preferredForm(current: String, typed: String): String =
        if (typed.count { it.isUpperCase() } < current.count { it.isUpperCase() }) typed else current

    /** Nouvel état après une utilisation conservée de [typed] (mot inconnu du dictionnaire embarqué). */
    fun kept(existing: PersonalWord?, typed: String, now: Long): PersonalWord {
        val uses = (existing?.uses ?: 0) + 1
        return PersonalWord(
            word = existing?.let { preferredForm(it.word, typed) } ?: typed,
            uses = uses,
            learned = existing?.learned == true || uses >= LearnAfterUses,
            lastUsedAt = now,
        )
    }

    /** Candidats à oublier : au-delà de [MaxCandidates], les moins récents. */
    fun candidatesToForget(words: List<PersonalWord>): List<PersonalWord> =
        words.filter { !it.learned && it.uses < LearnAfterUses }
            .sortedByDescending { it.lastUsedAt }
            .drop(MaxCandidates)

    /** Fréquence (échelle 1..255 du dictionnaire) d'un mot appris : au niveau des mots courants, un peu plus à l'usage. */
    fun learnedFrequency(uses: Int): Int = (LearnedBaseFrequency + FrequencyPerUse * (uses - LearnAfterUses).coerceIn(0, 6))

    /** Bonus d'un mot du dictionnaire embarqué choisi [uses] fois dans la barre. */
    fun boost(uses: Int): Int = BoostPerUse * uses.coerceIn(0, MaxBoostedUses)

    private const val BoostPerUse = 4
    private const val MaxBoostedUses = 8

    /** Plus grand [boost] : borne de l'élagage de la recherche. */
    const val MaxBoost = BoostPerUse * MaxBoostedUses
    private const val LearnedBaseFrequency = 120
    private const val FrequencyPerUse = 10

    /** Recherche dans la liste des réglages : sans casse ni accents (`ecole` trouve `École`). */
    fun matches(word: String, query: String): Boolean {
        val needle = query.trim().map(TypingCosts::fold).joinToString("")
        return needle.isEmpty() || word.map(TypingCosts::fold).joinToString("").contains(needle)
    }
}

/**
 * Dictionnaire personnel prêt pour le [SuggestionEngine] : les mots appris dans un petit arbre au
 * format du dictionnaire embarqué, et le bonus des mots embarqués choisis dans la barre, par nœud.
 */
class PersonalLexicon private constructor(val learned: Dictionary?, val boosts: Map<Int, Int>) {

    companion object {
        val Empty = PersonalLexicon(null, emptyMap())

        fun build(dictionary: Dictionary, words: List<PersonalWord>): PersonalLexicon {
            val learned = words.filter { it.learned }
                .associate { it.word to PersonalDictionaryRules.learnedFrequency(it.uses) }
            val boosts = HashMap<Int, Int>()
            for (word in words) {
                if (word.learned) continue
                val node = dictionary.findAnyCase(word.word).takeIf { it >= 0 } ?: continue
                val boost = PersonalDictionaryRules.boost(word.uses)
                if (boost > 0) boosts[node] = boost
            }
            return PersonalLexicon(learned.takeIf { it.isNotEmpty() }?.let(Dictionary::build), boosts)
        }
    }
}
