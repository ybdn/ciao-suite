package dev.ybdn.ciao.galerie.presentation.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.ybdn.ciao.galerie.R
import dev.ybdn.ciao.galerie.domain.util.CaptureDates
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoCard
import dev.ybdn.ciao.designsystem.components.NeoTone
import dev.ybdn.ciao.designsystem.theme.LabelMono
import dev.ybdn.ciao.designsystem.theme.NeoTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Saisie de la date et de l'heure au format `aaaa-MM-jj` et `HH:mm:ss`. */
object CaptureDateInput {
    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.US).withResolverStyle(ResolverStyle.STRICT)
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US).withResolverStyle(ResolverStyle.STRICT)

    fun formatDate(value: LocalDateTime?): String = value?.let(DATE::format).orEmpty()

    fun formatTime(value: LocalDateTime?): String = value?.let(TIME::format).orEmpty()

    /** null si la date ou l'heure est invalide ou incomplète. */
    fun parse(date: String, time: String): LocalDateTime? = runCatching {
        LocalDateTime.of(LocalDate.parse(date.trim(), DATE), LocalTime.parse(time.trim(), TIME))
    }.getOrNull()
}

/** « UTC+02:00 », ou « Inconnu ». */
@Composable
fun offsetLabel(minutes: Int?): String =
    minutes?.let { "UTC" + CaptureDates.formatOffset(it) } ?: stringResource(R.string.metadata_offset_unknown)

/** Bouton affichant le décalage UTC choisi ; ouvre la liste de −12:00 à +14:00 par quarts d'heure. */
@Composable
fun OffsetPicker(offsetMinutes: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.metadata_offset).uppercase(), style = LabelMono, color = NeoTheme.palette.content)
        NeoButton(offsetLabel(offsetMinutes), onClick = { open = true }, tone = NeoTone.Surface)
    }
    if (!open) return
    val options: List<Int?> = listOf<Int?>(null) + CaptureDates.OFFSET_OPTIONS_MINUTES
    Dialog(onDismissRequest = { open = false }) {
        NeoCard(modifier = Modifier.padding(8.dp)) {
            Text(stringResource(R.string.metadata_offset), style = MaterialTheme.typography.titleLarge)
            val initialIndex = options.indexOf(offsetMinutes).coerceAtLeast(0)
            LazyColumn(
                state = rememberLazyListState(initialFirstVisibleItemIndex = (initialIndex - 3).coerceAtLeast(0)),
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                items(options) { option ->
                    val selected = option == offsetMinutes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(option)
                                open = false
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                    ) {
                        Text(
                            offsetLabel(option),
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
