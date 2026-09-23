package dev.ybdn.ciao.clavier.domain.input

import kotlin.math.abs
import kotlin.math.sign

/**
 * Glissement sur la barre d'espace pour déplacer le curseur (apps/clavier/docs/spec-v1.md §6.1).
 * Tant que le doigt n'a pas parcouru [startThreshold], c'est un appui (espace) ; au-delà, chaque
 * [step] parcouru déplace le curseur d'un caractère, et l'espace n'est plus inséré au relâcher.
 * Les distances sont dans la même unité (pixels) que celles passées à [onMove].
 */
class SpaceCursorDrag(private val startThreshold: Float, private val step: Float) {

    var isDragging: Boolean = false
        private set

    /** Position (depuis l'appui) à laquelle le dernier pas a été compté. */
    private var anchor = 0f

    /**
     * [dx] : déplacement horizontal total depuis l'appui. Renvoie le nombre de caractères dont
     * déplacer le curseur depuis l'appel précédent (négatif vers la gauche).
     */
    fun onMove(dx: Float): Int {
        if (!isDragging) {
            if (abs(dx) < startThreshold) return 0
            isDragging = true
            // Le premier pas tombe dès le seuil franchi : le curseur réagit tout de suite.
            anchor = dx - dx.sign * step
        }
        val steps = ((dx - anchor) / step).toInt()
        anchor += steps * step
        return steps
    }
}
