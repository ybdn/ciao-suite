package dev.ybdn.ciaocloud.presentation.viewer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.ybdn.ciao.designsystem.components.stableNavigationBarsPadding
import dev.ybdn.ciaocloud.domain.model.EditAvailability
import dev.ybdn.ciaocloud.domain.model.EditUnavailableReason
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciaocloud.domain.model.GeoPoint
import dev.ybdn.ciaocloud.presentation.editor.messageRes
import dev.ybdn.ciaocloud.domain.model.GalleryLocation
import dev.ybdn.ciaocloud.domain.model.ExposureProgram
import dev.ybdn.ciaocloud.domain.model.HdrFormat
import dev.ybdn.ciaocloud.domain.model.MediaDetails
import dev.ybdn.ciaocloud.domain.model.MeteringMode
import dev.ybdn.ciaocloud.domain.model.SceneType
import dev.ybdn.ciaocloud.domain.util.MediaMetadataCodes
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoCard
import dev.ybdn.ciao.designsystem.components.NeoTag
import dev.ybdn.ciao.designsystem.components.NeoTone
import dev.ybdn.ciaocloud.presentation.gallery.formatDay
import dev.ybdn.ciaocloud.presentation.gallery.formatDuration
import dev.ybdn.ciao.designsystem.theme.NeoTheme
import dev.ybdn.ciaocloud.presentation.util.formatBytes
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Panneau d'infos : date et décalage, fichier, prise de vue, vidéo, GPS, image et emplacements. */
@Composable
fun InfoSheet(
    item: GalleryItem,
    loadDetails: suspend (GalleryItem) -> MediaDetails?,
    onDismiss: () -> Unit,
    metadataAvailability: EditAvailability = EditAvailability.Unavailable(EditUnavailableReason.UNSUPPORTED_FORMAT),
    onEditMetadata: (MediaDetails?) -> Unit = {},
    onCopyLocation: (GeoPoint) -> Unit = {},
) {
    // Relu après une modification (date de modification changée).
    val details by produceState<DetailsState>(
        DetailsState.Loading,
        item.key,
        item.phone?.dateModifiedEpochMillis,
        item.ssd?.lastModifiedEpochMillis,
    ) {
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
                .stableNavigationBarsPadding(),
        ) {
            val loaded = (details as? DetailsState.Loaded)?.details
            Text(captureDateText(item, loaded), style = MaterialTheme.typography.titleLarge)

            val editReason = (metadataAvailability as? EditAvailability.Unavailable)?.reason
            if (details is DetailsState.Loaded && (editReason == null || !editReason.hidesAction)) {
                val context = LocalContext.current
                NeoButton(
                    text = stringResource(R.string.metadata_edit),
                    tone = NeoTone.Yellow,
                    onClick = {
                        if (editReason == null) {
                            onEditMetadata(loaded)
                        } else {
                            Toast.makeText(context, editReason.messageRes(), Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }

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

            loaded?.let { cameraLines(it) }?.let { lines -> InfoCard(R.string.info_camera, NeoTone.Yellow, lines) }
            loaded?.let { videoLines(it) }?.let { lines -> InfoCard(R.string.info_video, NeoTone.Coral, lines) }

            val latitude = loaded?.latitude
            val longitude = loaded?.longitude
            if (latitude != null && longitude != null) {
                val context = LocalContext.current
                NeoCard {
                    NeoTag(stringResource(R.string.info_location), tone = NeoTone.Teal)
                    InfoLine(String.format(Locale.US, "%.6f, %.6f", latitude, longitude), monospace = true)
                    loaded.altitudeMeters?.let {
                        InfoLine(stringResource(R.string.info_altitude, String.format(Locale.FRENCH, "%.0f", it)))
                    }
                    loaded.directionDegrees?.let {
                        InfoLine(stringResource(R.string.info_direction, String.format(Locale.FRENCH, "%.0f", it), cardinalPoint(it)))
                    }
                    NeoButton(
                        text = stringResource(R.string.metadata_copy_location),
                        tone = NeoTone.Surface,
                        onClick = {
                            onCopyLocation(GeoPoint(latitude, longitude, loaded.altitudeMeters))
                            Toast.makeText(context, R.string.metadata_location_copied, Toast.LENGTH_SHORT).show()
                        },
                    )
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

            loaded?.let { imageLines(it) }?.let { lines -> InfoCard(R.string.info_image, NeoTone.Sky, lines) }

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

/** Carte titrée, omise quand aucune ligne n'est renseignée. */
@Composable
private fun InfoCard(titleRes: Int, tone: NeoTone, lines: List<String>) {
    if (lines.isEmpty()) return
    NeoCard {
        NeoTag(stringResource(titleRes), tone = tone)
        lines.forEach { InfoLine(it) }
    }
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

@Composable
private fun cameraLines(details: MediaDetails): List<String> = buildList {
    val device = listOfNotNull(details.cameraMake, details.cameraModel)
        .distinct()
        // Évite « Google Google Pixel 10 Pro » quand le modèle reprend la marque.
        .let { parts -> if (parts.size == 2 && parts[1].startsWith(parts[0], ignoreCase = true)) listOf(parts[1]) else parts }
        .joinToString(" ")
    if (device.isNotEmpty()) add(device)
    val lens = listOfNotNull(details.lensMake?.takeUnless { make -> details.lensModel?.startsWith(make, ignoreCase = true) == true }, details.lensModel)
        .joinToString(" ")
    if (lens.isNotEmpty()) add(lens)
    val focal = details.focalLengthMm?.let { String.format(Locale.FRENCH, "%.1f mm", it) }
    val exposure = listOfNotNull(
        details.fNumber?.let { String.format(Locale.FRENCH, "ƒ/%.1f", it) },
        details.exposureTimeSeconds?.let(::formatExposure),
        when {
            focal != null && details.focalLength35mm != null -> stringResource(R.string.info_focal_35mm, focal, details.focalLength35mm)
            else -> focal
        },
        details.iso?.let { "ISO $it" },
    ).joinToString(" · ")
    if (exposure.isNotEmpty()) add(exposure)
    details.exposureBiasEv?.takeIf { abs(it) >= 0.05 }?.let {
        add(stringResource(R.string.info_exposure_bias, String.format(Locale.FRENCH, "%+.1f", it)))
    }
    details.exposureProgram?.let { add(stringResource(R.string.info_exposure_program, stringResource(it.labelRes))) }
    details.meteringMode?.let { add(stringResource(R.string.info_metering, stringResource(it.labelRes))) }
    details.sceneType?.let { add(stringResource(R.string.info_scene, stringResource(it.labelRes))) }
    details.flashFired?.let { add(stringResource(if (it) R.string.info_flash_fired else R.string.info_flash_not_fired)) }
    details.whiteBalanceManual?.let {
        add(stringResource(if (it) R.string.info_white_balance_manual else R.string.info_white_balance_auto))
    }
    details.digitalZoomRatio?.let { add(stringResource(R.string.info_digital_zoom, String.format(Locale.FRENCH, "%.1f", it))) }
    details.subjectDistanceMeters?.let { add(stringResource(R.string.info_subject_distance, String.format(Locale.FRENCH, "%.2f", it))) }
}

@Composable
private fun videoLines(details: MediaDetails): List<String> = buildList {
    val video = listOfNotNull(
        details.videoCodecMimeType?.let(MediaMetadataCodes::codecName),
        details.frameRate?.let { stringResource(R.string.info_frame_rate, formatDecimal(it)) },
        details.bitrateBitsPerSecond?.let {
            if (it >= 1_000_000) {
                stringResource(R.string.info_bitrate_mbps, String.format(Locale.FRENCH, "%.1f", it / 1_000_000.0))
            } else {
                stringResource(R.string.info_bitrate_kbps, it / 1000)
            }
        },
        details.hdrFormat?.let {
            when (it) {
                HdrFormat.HDR10 -> "HDR10"
                HdrFormat.HLG -> "HDR HLG"
            }
        },
    ).joinToString(" · ")
    if (video.isNotEmpty()) add(video)
    if (details.hasAudio == false) {
        add(stringResource(R.string.info_no_audio))
    } else {
        val audio = listOfNotNull(
            details.audioCodecMimeType?.let(MediaMetadataCodes::codecName),
            details.audioSampleRateHz?.let { formatDecimal(it / 1000.0) + " kHz" },
            details.audioChannels?.let {
                when (it) {
                    1 -> stringResource(R.string.info_audio_mono)
                    2 -> stringResource(R.string.info_audio_stereo)
                    else -> stringResource(R.string.info_audio_channels, it)
                }
            },
        ).joinToString(" · ")
        if (audio.isNotEmpty()) add(stringResource(R.string.info_audio_codec, audio))
    }
    details.containerMimeType?.let { add(stringResource(R.string.info_container, MediaMetadataCodes.containerName(it))) }
}

@Composable
private fun imageLines(details: MediaDetails): List<String> = buildList {
    details.description?.let(::add)
    details.rotationDegrees?.takeIf { it != 0 }?.let { add(stringResource(R.string.info_rotation, it)) }
    details.software?.let { add(stringResource(R.string.info_software, it)) }
    details.artist?.let { add(stringResource(R.string.info_artist, it)) }
    details.copyright?.let { add(stringResource(R.string.info_copyright, it)) }
}

private val ExposureProgram.labelRes: Int
    get() = when (this) {
        ExposureProgram.MANUAL -> R.string.info_exposure_program_manual
        ExposureProgram.NORMAL -> R.string.info_exposure_program_normal
        ExposureProgram.APERTURE_PRIORITY -> R.string.info_exposure_program_aperture
        ExposureProgram.SHUTTER_PRIORITY -> R.string.info_exposure_program_shutter
        ExposureProgram.CREATIVE -> R.string.info_exposure_program_creative
        ExposureProgram.ACTION -> R.string.info_exposure_program_action
        ExposureProgram.PORTRAIT -> R.string.info_exposure_program_portrait
        ExposureProgram.LANDSCAPE -> R.string.info_exposure_program_landscape
    }

private val MeteringMode.labelRes: Int
    get() = when (this) {
        MeteringMode.AVERAGE -> R.string.info_metering_average
        MeteringMode.CENTER_WEIGHTED -> R.string.info_metering_center
        MeteringMode.SPOT -> R.string.info_metering_spot
        MeteringMode.MULTI_SPOT -> R.string.info_metering_multi_spot
        MeteringMode.PATTERN -> R.string.info_metering_pattern
        MeteringMode.PARTIAL -> R.string.info_metering_partial
    }

private val SceneType.labelRes: Int
    get() = when (this) {
        SceneType.LANDSCAPE -> R.string.info_scene_landscape
        SceneType.PORTRAIT -> R.string.info_scene_portrait
        SceneType.NIGHT -> R.string.info_scene_night
    }

/** « 30 », « 29,97 », « 44,1 » : décimales seulement si utiles. */
private fun formatDecimal(value: Double): String =
    String.format(Locale.FRENCH, "%.2f", value).trimEnd('0').trimEnd(',')

private val CARDINAL_POINTS = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")

private fun cardinalPoint(degrees: Double): String = CARDINAL_POINTS[(Math.round(degrees / 45.0).toInt()) % 8]

/** « 1/120 s » sous la seconde, « 2,5 s » au-delà. */
private fun formatExposure(seconds: Double): String =
    if (seconds < 1) "1/${Math.round(1 / seconds)} s" else String.format(Locale.FRENCH, "%.1f s", seconds)
