package dev.ybdn.ciao.galerie.presentation.gallery

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FORMATTER = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)
private val MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)

/** « Mercredi 16 septembre 2026 ». */
fun formatDay(date: LocalDate): String = DAY_FORMATTER.format(date).replaceFirstChar { it.titlecase(Locale.FRENCH) }

/** « Septembre 2026 ». */
fun formatMonth(date: LocalDate): String = MONTH_FORMATTER.format(date).replaceFirstChar { it.titlecase(Locale.FRENCH) }

/** « 0:42 », « 12:05 », « 1:02:03 ». */
fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
