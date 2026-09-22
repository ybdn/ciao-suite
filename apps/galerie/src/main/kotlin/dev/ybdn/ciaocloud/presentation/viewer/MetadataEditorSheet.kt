package dev.ybdn.ciaocloud.presentation.viewer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.CaptureTimestamp
import dev.ybdn.ciaocloud.domain.model.FieldChange
import dev.ybdn.ciaocloud.domain.util.CaptureDates
import dev.ybdn.ciaocloud.presentation.theme.Brick
import androidx.compose.ui.text.font.FontFamily
import java.time.ZoneId
import dev.ybdn.ciaocloud.domain.model.GeoPoint
import dev.ybdn.ciaocloud.domain.model.MediaDetails
import dev.ybdn.ciaocloud.domain.model.MetadataChanges
import dev.ybdn.ciaocloud.domain.util.ExifWritePlan
import dev.ybdn.ciaocloud.domain.util.GpsCoordinateParser
import dev.ybdn.ciaocloud.presentation.components.NeoButton
import dev.ybdn.ciaocloud.presentation.components.NeoCard
import dev.ybdn.ciaocloud.presentation.components.NeoNotice
import dev.ybdn.ciaocloud.presentation.components.NeoTag
import dev.ybdn.ciaocloud.presentation.components.NeoTextField
import dev.ybdn.ciaocloud.presentation.components.NeoTone
import dev.ybdn.ciaocloud.presentation.components.stableNavigationBarsPadding
import dev.ybdn.ciaocloud.presentation.theme.NeoTheme
import java.util.Locale

/**
 * Modification des métadonnées d'une photo, en place : textes, position (saisie, collage, suppression)
 * et nettoyage confidentialité. Seuls les champs modifiés sont écrits.
 */
@Composable
fun MetadataEditorSheet(
    details: MediaDetails?,
    clipboardLocation: GeoPoint?,
    onSave: (MetadataChanges) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialDescription = details?.description.orEmpty()
    val initialArtist = details?.artist.orEmpty()
    val initialCopyright = details?.copyright.orEmpty()
    val initialLocation = formatLocation(details?.latitude, details?.longitude)
    val initialAltitude = details?.altitudeMeters?.let { formatAltitude(it) }.orEmpty()

    val initialDate = CaptureDateInput.formatDate(details?.captureLocalDateTime)
    val initialTime = CaptureDateInput.formatTime(details?.captureLocalDateTime)
    val initialOffset = details?.captureUtcOffsetMinutes
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var time by rememberSaveable { mutableStateOf(initialTime) }
    var offset by rememberSaveable { mutableStateOf(initialOffset) }
    var dateError by rememberSaveable { mutableStateOf(false) }

    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var artist by rememberSaveable { mutableStateOf(initialArtist) }
    var copyright by rememberSaveable { mutableStateOf(initialCopyright) }
    var location by rememberSaveable { mutableStateOf(initialLocation) }
    var altitude by rememberSaveable { mutableStateOf(initialAltitude) }
    var locationError by rememberSaveable { mutableStateOf(false) }
    var confirmCleanup by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    if (confirmCleanup) {
        SensitiveDataDialog(
            onConfirm = {
                confirmCleanup = false
                onSave(MetadataChanges(removeSensitiveData = true))
            },
            onDismiss = { confirmCleanup = false },
        )
    }

    fun textChange(initial: String, current: String): FieldChange<String> =
        if (current.trim() == initial.trim()) FieldChange.Keep else FieldChange.Set(current)

    /** null si la position saisie est invalide. */
    fun locationChange(): FieldChange<GeoPoint>? {
        if (location == initialLocation && altitude == initialAltitude) return FieldChange.Keep
        if (location.isBlank()) return if (initialLocation.isEmpty()) FieldChange.Keep else FieldChange.Remove
        val point = GpsCoordinateParser.parse(location) ?: return null
        val altitudeValue = altitude.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.let { it.toDoubleOrNull() ?: return null }
        return FieldChange.Set(point.copy(altitudeMeters = altitudeValue))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
            Text(stringResource(R.string.metadata_title), style = MaterialTheme.typography.titleLarge)

            NeoCard {
                NeoTag(stringResource(R.string.metadata_date), tone = NeoTone.Yellow)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeoTextField(
                        label = stringResource(R.string.metadata_day),
                        value = date,
                        onValueChange = {
                            date = it
                            dateError = false
                        },
                        placeholder = "2025-04-20",
                        modifier = Modifier.weight(1f),
                    )
                    NeoTextField(
                        label = stringResource(R.string.metadata_time),
                        value = time,
                        onValueChange = {
                            time = it
                            dateError = false
                        },
                        placeholder = "18:45:00",
                        modifier = Modifier.weight(1f),
                    )
                }
                OffsetPicker(offsetMinutes = offset, onSelect = { offset = it })
                val parsed = CaptureDateInput.parse(date, time)
                when {
                    dateError || (parsed == null && (date.isNotBlank() || time.isNotBlank())) ->
                        Text(stringResource(R.string.metadata_invalid_date), style = MaterialTheme.typography.bodySmall, color = Brick)
                    parsed != null -> CaptureDates.targetDirectory(CaptureTimestamp(parsed, offset), ZoneId.systemDefault())?.let {
                        Text(stringResource(R.string.metadata_folder_preview, it), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            NeoCard {
                NeoTag(stringResource(R.string.metadata_texts), tone = NeoTone.Sky)
                NeoTextField(
                    stringResource(R.string.metadata_description),
                    description,
                    { description = it.take(ExifWritePlan.MAX_TEXT_LENGTH) },
                    singleLine = false,
                )
                NeoTextField(stringResource(R.string.metadata_artist), artist, { artist = it.take(ExifWritePlan.MAX_TEXT_LENGTH) })
                NeoTextField(stringResource(R.string.metadata_copyright), copyright, { copyright = it.take(ExifWritePlan.MAX_TEXT_LENGTH) })
            }

            NeoCard {
                NeoTag(stringResource(R.string.info_location), tone = NeoTone.Teal)
                NeoTextField(
                    label = stringResource(R.string.metadata_coordinates),
                    value = location,
                    onValueChange = {
                        location = it
                        locationError = false
                    },
                    placeholder = "48.8584, 2.2945",
                    error = if (locationError) stringResource(R.string.metadata_invalid_coordinates) else null,
                )
                NeoTextField(
                    label = stringResource(R.string.metadata_altitude),
                    value = altitude,
                    onValueChange = {
                        altitude = it
                        locationError = false
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeoButton(
                        stringResource(R.string.metadata_paste_location),
                        onClick = {
                            clipboardLocation?.let {
                                location = formatLocation(it.latitude, it.longitude)
                                altitude = it.altitudeMeters?.let(::formatAltitude).orEmpty()
                                locationError = false
                            }
                        },
                        enabled = clipboardLocation != null,
                        tone = NeoTone.Surface,
                        modifier = Modifier.weight(1f),
                    )
                    NeoButton(
                        stringResource(R.string.metadata_remove_location),
                        onClick = {
                            location = ""
                            altitude = ""
                            locationError = false
                        },
                        enabled = location.isNotEmpty(),
                        tone = NeoTone.Surface,
                        modifier = Modifier.weight(1f),
                    )
                }
                NeoButton(
                    stringResource(R.string.metadata_check_in_maps),
                    onClick = {
                        val point = GpsCoordinateParser.parse(location)
                        if (point == null) {
                            locationError = true
                        } else {
                            val geo = Uri.parse("geo:${point.latitude},${point.longitude}?q=${point.latitude},${point.longitude}")
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, geo))
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, R.string.info_no_maps_app, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = location.isNotBlank(),
                    tone = NeoTone.Surface,
                )
            }

            NeoButton(
                stringResource(R.string.editor_save),
                onClick = {
                    val locationChange = locationChange()
                    val dateChanged = date.trim() != initialDate || time.trim() != initialTime
                    val newDate = if (dateChanged) CaptureDateInput.parse(date, time) else null
                    when {
                        locationChange == null -> locationError = true
                        dateChanged && newDate == null -> dateError = true
                        else -> onSave(
                            MetadataChanges(
                                description = textChange(initialDescription, description),
                                artist = textChange(initialArtist, artist),
                                copyright = textChange(initialCopyright, copyright),
                                location = locationChange,
                                captureDateTime = newDate?.let { FieldChange.Set(it) } ?: FieldChange.Keep,
                                utcOffsetMinutes = when {
                                    offset == initialOffset -> FieldChange.Keep
                                    offset == null -> FieldChange.Remove
                                    else -> FieldChange.Set(offset!!)
                                },
                            ),
                        )
                    }
                },
            )
            NeoButton(
                stringResource(R.string.metadata_remove_sensitive),
                onClick = { confirmCleanup = true },
                tone = NeoTone.Brick,
            )
        }
    }
}

@Composable
private fun SensitiveDataDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        NeoCard(modifier = Modifier.padding(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.metadata_remove_sensitive_title), style = MaterialTheme.typography.titleLarge)
                NeoNotice(stringResource(R.string.metadata_remove_sensitive_list), tone = NeoTone.Coral)
                Text(stringResource(R.string.metadata_remove_sensitive_kept), style = MaterialTheme.typography.bodySmall)
                NeoButton(stringResource(R.string.metadata_remove_sensitive_confirm), onClick = onConfirm, tone = NeoTone.Brick)
                NeoButton(stringResource(R.string.delete_confirm_cancel), onClick = onDismiss, tone = NeoTone.Surface)
            }
        }
    }
}

private fun formatLocation(latitude: Double?, longitude: Double?): String =
    if (latitude == null || longitude == null) "" else String.format(Locale.US, "%.6f, %.6f", latitude, longitude)

private fun formatAltitude(meters: Double): String = String.format(Locale.US, "%.1f", meters)
