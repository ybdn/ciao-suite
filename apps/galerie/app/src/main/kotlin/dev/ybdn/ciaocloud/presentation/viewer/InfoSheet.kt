package dev.ybdn.ciaocloud.presentation.viewer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.GalleryLocation
import dev.ybdn.ciaocloud.domain.model.MediaDetails
import dev.ybdn.ciaocloud.presentation.components.NeoButton
import dev.ybdn.ciaocloud.presentation.components.NeoCard
import dev.ybdn.ciaocloud.presentation.components.NeoTag
import dev.ybdn.ciaocloud.presentation.components.NeoTone
import dev.ybdn.ciaocloud.presentation.gallery.formatDay
import dev.ybdn.ciaocloud.presentation.gallery.formatDuration
import dev.ybdn.ciaocloud.presentation.theme.NeoTheme
import dev.ybdn.ciaocloud.presentation.util.formatBytes
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Panneau d'infos : date et décalage, fichier, prise de vue, GPS et emplacements. */
@Composable
fun InfoSheet(
    item: GalleryItem,
    loadDetails: suspend (GalleryItem) -> MediaDetails?,
    onDismiss: () -> Unit,
) {
    val details by produceState<DetailsState>(DetailsState.Loading, item.key) {
        value = DetailsState.Loaded(loadDetails(item))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NeoTheme.palette.page,
        contentColor = NeoTheme.palette.content,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            val loaded = (details as? DetailsState.Loaded)?.details
            Text(captureDateText(item, loaded), style = MaterialTheme.typography.titleLarge)

            NeoCard {
                NeoTag(stringResource(R.string.info_file), tone = NeoTone.Sky)
                InfoLine(item.displayName, monospace = true)
                val width = loaded?.width ?: item.phone?.width?.takeIf { it > 0 }
                val height = loaded?.height ?: item.phone?.height?.takeIf { it > 0 }
                InfoLine(
                    listOfNotNull(
                        formatBytes(item.sizeBytes),
                        if (width != null && height != null) {
                            stringResource(R.string.info_dimensions, width, height, width * height / 1_000_000.0)
                        } else {
                            null
                        },
                        (loaded?.durationMillis ?: item.phone?.durationMillis)?.let(::formatDuration),
                    ).joinToString(" · "),
                )
            }

            if (details == DetailsState.Loading) CircularProgressIndicator()

            loaded?.let { cameraLines(it) }?.takeIf { it.isNotEmpty() }?.let { lines ->
                NeoCard {
                    NeoTag(stringResource(R.string.info_camera), tone = NeoTone.Yellow)
                    lines.forEach { InfoLine(it) }
                }
            }

            val latitude = loaded?.latitude
            val longitude = loaded?.longitude
            if (latitude != null && longitude != null) {
                val context = LocalContext.current
                NeoCard {
                    NeoTag(stringResource(R.string.info_location), tone = NeoTone.Teal)
                    InfoLine(String.format(Locale.US, "%.6f, %.6f", latitude, longitude), monospace = true)
                    NeoButton(
                        text = stringResource(R.string.info_open_maps),
                        tone = NeoTone.Surface,
                        onClick = {
                            val geo = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, geo))
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, R.string.info_no_maps_app, Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                }
            }

            NeoCard {
                NeoTag(stringResource(R.string.info_storage), tone = NeoTone.Lime)
                item.phone?.let { InfoLine(stringResource(R.string.info_on_phone, (it.relativePath.orEmpty()) + it.displayName), monospace = true) }
                item.ssd?.let { InfoLine(stringResource(R.string.info_on_ssd, it.relativePath), monospace = true) }
                InfoLine(
                    stringResource(
                        when (item.location) {
                            GalleryLocation.PHONE -> R.string.info_status_not_backed_up
                            GalleryLocation.BOTH -> R.string.info_status_backed_up
                            GalleryLocation.SSD -> R.string.info_status_ssd_only
                        },
                    ),
                )
            }
        }
    }
}

private sealed interface DetailsState {
    data object Loading : DetailsState
    data class Loaded(val details: MediaDetails?) : DetailsState
}

@Composable
private fun InfoLine(text: String, monospace: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = if (monospace) FontFamily.Monospace else null,
    )
}

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.FRENCH)

/** « Mardi 15 septembre 2026 · 13:44:00 (UTC+02:00) » : heure locale du lieu de prise de vue si connue. */
private fun captureDateText(item: GalleryItem, details: MediaDetails?): String {
    val local = details?.captureLocalDateTime
    if (local != null) {
        val offset = details.captureUtcOffsetMinutes?.let { " (UTC${ZoneOffset.ofTotalSeconds(it * 60).id.replace("Z", "+00:00")})" }.orEmpty()
        return "${formatDay(local.toLocalDate())} · ${TIME_FORMATTER.format(local)}$offset"
    }
    val instant = item.sortEpochMillis ?: return formatDay(item.captureDate)
    val zoned = Instant.ofEpochMilli(instant).atZone(ZoneId.systemDefault())
    return "${formatDay(item.captureDate)} · ${TIME_FORMATTER.format(zoned)}"
}

private fun cameraLines(details: MediaDetails): List<String> = buildList {
    val device = listOfNotNull(details.cameraMake, details.cameraModel)
        .distinct()
        // Évite « Google Google Pixel 10 Pro » quand le modèle reprend la marque.
        .let { parts -> if (parts.size == 2 && parts[1].startsWith(parts[0], ignoreCase = true)) listOf(parts[1]) else parts }
        .joinToString(" ")
    if (device.isNotEmpty()) add(device)
    details.lensModel?.let(::add)
    val exposure = listOfNotNull(
        details.fNumber?.let { String.format(Locale.FRENCH, "ƒ/%.1f", it) },
        details.exposureTimeSeconds?.let(::formatExposure),
        details.focalLengthMm?.let { String.format(Locale.FRENCH, "%.1f mm", it) },
        details.iso?.let { "ISO $it" },
    ).joinToString(" · ")
    if (exposure.isNotEmpty()) add(exposure)
}

/** « 1/120 s » sous la seconde, « 2,5 s » au-delà. */
private fun formatExposure(seconds: Double): String =
    if (seconds < 1) "1/${Math.round(1 / seconds)} s" else String.format(Locale.FRENCH, "%.1f s", seconds)
