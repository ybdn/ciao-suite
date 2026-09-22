package dev.ybdn.ciaocloud.domain.util

import dev.ybdn.ciaocloud.domain.model.MediaFile
import dev.ybdn.ciaocloud.domain.model.MediaType

/**
 * Les vidéos ne portent qu'un instant UTC, sans le fuseau du lieu de tournage : on leur attribue le
 * décalage horaire de la photo du même scan la plus proche dans le temps (dans la limite de
 * [MAX_DISTANCE_MILLIS]), pour qu'une vidéo tournée à l'étranger soit rangée au bon jour local.
 */
object CaptureOffsetInferrer {

    const val MAX_DISTANCE_MILLIS = 12L * 60 * 60 * 1000

    /** Renvoie [media] où chaque vidéo sans décalage connu reçoit celui de sa photo voisine, si elle existe. */
    fun inferVideoOffsets(media: List<MediaFile>): List<MediaFile> {
        val references = media
            .filter { it.mediaType == MediaType.PHOTO && it.captureUtcOffsetMinutes != null }
            .map { OffsetReference(it.effectiveDateEpochMillis, it.captureUtcOffsetMinutes!!) }
            .sortedBy { it.epochMillis }
        if (references.isEmpty()) return media

        return media.map { file ->
            if (file.mediaType != MediaType.VIDEO || file.captureUtcOffsetMinutes != null) {
                file
            } else {
                file.copy(captureUtcOffsetMinutes = inferOffset(file.effectiveDateEpochMillis, references))
            }
        }
    }

    /**
     * @param sortedReferences photos à décalage connu, triées par instant croissant.
     * @return le décalage de la référence la plus proche (l'antérieure en cas d'égalité), ou null
     * si aucune n'est à moins de [MAX_DISTANCE_MILLIS].
     */
    fun inferOffset(epochMillis: Long, sortedReferences: List<OffsetReference>): Int? {
        // Index de la première référence strictement postérieure à l'instant.
        var low = 0
        var high = sortedReferences.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (sortedReferences[mid].epochMillis <= epochMillis) low = mid + 1 else high = mid
        }
        val before = sortedReferences.getOrNull(low - 1)
        val after = sortedReferences.getOrNull(low)

        val closest = when {
            before == null -> after
            after == null -> before
            epochMillis - before.epochMillis <= after.epochMillis - epochMillis -> before
            else -> after
        } ?: return null

        return closest.utcOffsetMinutes.takeIf { kotlin.math.abs(closest.epochMillis - epochMillis) <= MAX_DISTANCE_MILLIS }
    }
}

data class OffsetReference(
    val epochMillis: Long,
    val utcOffsetMinutes: Int,
)
