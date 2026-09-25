package dev.ybdn.ciao.galerie.domain.util

import dev.ybdn.ciao.galerie.domain.model.EditRecipe

/**
 * Historique Annuler / Rétablir de l'éditeur, limité à [capacity] étapes. Un geste continu (curseur,
 * cadre, molette) ne compte que pour une étape : l'état est enregistré au début, validé à la fin.
 */
class EditHistory(
    initial: EditRecipe = EditRecipe(),
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val undoStack = ArrayDeque<EditRecipe>()
    private val redoStack = ArrayDeque<EditRecipe>()

    var current: EditRecipe = initial
        private set

    /** État au début du geste en cours, null hors geste. */
    private var gestureStart: EditRecipe? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Modification ponctuelle (bouton) : une étape. */
    fun apply(recipe: EditRecipe) {
        if (gestureStart != null) {
            current = recipe
            return
        }
        push(current, recipe)
    }

    fun beginGesture() {
        if (gestureStart == null) gestureStart = current
    }

    /** Étape intermédiaire d'un geste : affichée, pas encore enregistrée. */
    fun update(recipe: EditRecipe) {
        current = recipe
    }

    fun endGesture() {
        val start = gestureStart ?: return
        gestureStart = null
        val end = current
        current = start
        push(start, end)
    }

    /** Nouvelle session : recette initiale, historique vide. */
    fun reset(recipe: EditRecipe = EditRecipe()) {
        undoStack.clear()
        redoStack.clear()
        gestureStart = null
        current = recipe
    }

    fun undo(): EditRecipe {
        val previous = undoStack.removeLastOrNull() ?: return current
        redoStack.addLast(current)
        current = previous
        return current
    }

    fun redo(): EditRecipe {
        val next = redoStack.removeLastOrNull() ?: return current
        undoStack.addLast(current)
        current = next
        return current
    }

    private fun push(from: EditRecipe, to: EditRecipe) {
        if (from == to) return
        undoStack.addLast(from)
        while (undoStack.size > capacity) undoStack.removeFirst()
        redoStack.clear()
        current = to
    }

    companion object {
        const val DEFAULT_CAPACITY = 50
    }
}
