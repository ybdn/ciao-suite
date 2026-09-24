package dev.ybdn.ciao.clavier.ime

import dev.ybdn.ciao.clavier.domain.suggest.SuggestionEngine
import dev.ybdn.ciao.clavier.domain.suggest.WordAtCursor
import dev.ybdn.ciao.clavier.domain.suggest.matchCasing

/** Suggestions calculées pour [word] : la barre à afficher et la correction à appliquer, s'il y en a une. */
class ComputedSuggestions(val word: WordAtCursor, val bar: SuggestionBar, val autocorrection: String?)

/**
 * Barre de suggestions pour le mot en cours (apps/clavier/docs/spec-v1.md §7.1) : la meilleure
 * proposition au centre. Quand l'autocorrection va remplacer le mot, il reste proposé tel que
 * tapé à gauche, pour le garder. Sans mot en cours, les mots qui suivent le plus souvent le
 * précédent (§7.2).
 *
 * [rejected] : mots dont l'autocorrection a été annulée, plus jamais corrigés pendant la saisie.
 */
fun computeSuggestions(
    engine: SuggestionEngine,
    word: WordAtCursor,
    autocorrect: Boolean,
    rejected: Set<String>,
): ComputedSuggestions {
    if (word.word.isEmpty()) {
        val predictions = word.previousWord?.let(engine::predict).orEmpty()
        return ComputedSuggestions(word, SuggestionBar(centered(predictions, SuggestionItem.Kind.Prediction)), null)
    }
    val result = engine.suggest(word.word, word.previousWord)
    val words = result.words.map { matchCasing(word.word, it) }.distinct()
    val correction = result.autocorrection
        ?.takeIf { autocorrect && word.word.lowercase() !in rejected }
        ?.let { matchCasing(word.word, it) }
        ?.takeIf { it != word.word }
    val slots = if (correction != null) {
        listOf(
            SuggestionItem(word.word, SuggestionItem.Kind.Typed),
            SuggestionItem(correction, SuggestionItem.Kind.Autocorrection),
            words.firstOrNull { it != correction }?.let { SuggestionItem(it, SuggestionItem.Kind.Word) },
        )
    } else {
        centered(words, SuggestionItem.Kind.Word)
    }
    return ComputedSuggestions(word, SuggestionBar(slots, forTypedWord = true), correction)
}

/** Du plus au moins probable → gauche, centre, droite : la meilleure au centre. */
private fun centered(words: List<String>, kind: SuggestionItem.Kind): List<SuggestionItem?> =
    if (words.isEmpty()) emptyList() else listOf(1, 0, 2).map { index -> words.getOrNull(index)?.let { SuggestionItem(it, kind) } }
