package dev.ybdn.ciaocloud.presentation.util

import java.util.Locale
import kotlin.math.pow

/** Formate une taille en octets en une chaîne lisible ("12,3 Mo", "1,2 Go"). */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 Mo"
    val units = arrayOf("o", "Ko", "Mo", "Go", "To")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / 1024.0.pow(digitGroups)
    return String.format(Locale.FRANCE, "%.1f %s", value, units[digitGroups])
}
