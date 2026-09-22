package dev.ybdn.ciaocloud.presentation.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.DateShift
import dev.ybdn.ciaocloud.domain.model.FieldChange
import dev.ybdn.ciaocloud.domain.model.GeoPoint
import dev.ybdn.ciaocloud.domain.model.MetadataChanges
import dev.ybdn.ciaocloud.domain.model.MetadataEditPreview
import dev.ybdn.ciaocloud.domain.util.GpsCoordinateParser
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoCard
import dev.ybdn.ciao.designsystem.components.NeoNotice
import dev.ybdn.ciao.designsystem.components.NeoProgressBar
import dev.ybdn.ciao.designsystem.components.NeoSegmentedChoice
import dev.ybdn.ciao.designsystem.components.NeoTag
import dev.ybdn.ciao.designsystem.components.NeoTextField
import dev.ybdn.ciao.designsystem.components.NeoTone
import dev.ybdn.ciao.designsystem.components.stableNavigationBarsPadding
import dev.ybdn.ciao.designsystem.theme.NeoTheme
import dev.ybdn.ciaocloud.presentation.viewer.OffsetPicker
import java.util.Locale

/**
 * Modifications groupées des infos (spec v3 C6) : chaque carte applique une seule modification à
 * toutes les photos sélectionnées, après récapitulatif.
 */
@Composable
fun GroupMetadataSheet(
    count: Int,
    clipboardLocation: GeoPoint?,
    onApply: (MetadataChanges) -> Unit,
    onDismiss: () -> Unit,
) {
    var days by rememberSaveable { mutableStateOf("") }
    var hours by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }
    var backwards by rememberSaveable { mutableStateOf(false) }
    var offset by rememberSaveable { mutableStateOf<Int?>(null) }
    var location by rememberSaveable { mutableStateOf("") }
    var locationError by rememberSaveable { mutableStateOf(false) }
    var description by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf("") }
    var copyright by rememberSaveable { mutableStateOf("") }

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
            Text(pluralStringResource(R.plurals.group_metadata_title, count, count), style = MaterialTheme.typography.titleLarge)

            NeoCard {
                NeoTag(stringResource(R.string.group_metadata_shift), tone = NeoTone.Yellow)
                NeoSegmentedChoice(
                    options = listOf(stringResource(R.string.group_metadata_forward), stringResource(R.string.group_metadata_backward)),
                    selectedIndex = if (backwards) 1 else 0,
                    onSelect = { backwards = it == 1 },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val number = KeyboardOptions(keyboardType = KeyboardType.Number)
                    NeoTextField(stringResource(R.string.group_metadata_days), days, { days = it.filter(Char::isDigit) }, Modifier.weight(1f), keyboardOptions = number)
                    NeoTextField(stringResource(R.string.group_metadata_hours), hours, { hours = it.filter(Char::isDigit) }, Modifier.weight(1f), keyboardOptions = number)
                    NeoTextField(stringResource(R.string.group_metadata_minutes), minutes, { minutes = it.filter(Char::isDigit) }, Modifier.weight(1f), keyboardOptions = number)
                }
                val shift = DateShift(days.toIntOrNull() ?: 0, hours.toIntOrNull() ?: 0, minutes.toIntOrNull() ?: 0, backwards)
                NeoButton(
                    stringResource(R.string.group_metadata_apply),
                    onClick = { onApply(MetadataChanges(dateShift = shift)) },
                    enabled = !shift.isZero,
                    tone = NeoTone.Pink,
                )
            }

            NeoCard {
                NeoTag(stringResource(R.string.group_metadata_time_zone), tone = NeoTone.Sky)
                Text(stringResource(R.string.group_metadata_time_zone_hint), style = MaterialTheme.typography.bodySmall)
                OffsetPicker(offsetMinutes = offset, onSelect = { offset = it })
                NeoButton(
                    stringResource(R.string.group_metadata_apply),
                    onClick = {
                        onApply(MetadataChanges(utcOffsetMinutes = offset?.let { FieldChange.Set(it) } ?: FieldChange.Remove))
                    },
                    tone = NeoTone.Pink,
                )
            }

            NeoCard {
                NeoTag(stringResource(R.string.info_location), tone = NeoTone.Lavender)
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
                NeoButton(
                    stringResource(R.string.metadata_paste_location),
                    onClick = {
                        clipboardLocation?.let { location = String.format(Locale.US, "%.6f, %.6f", it.latitude, it.longitude) }
                    },
                    enabled = clipboardLocation != null,
                    tone = NeoTone.Surface,
                )
                NeoButton(
                    stringResource(R.string.group_metadata_apply),
                    onClick = {
                        val point = GpsCoordinateParser.parse(location)
                        if (point == null) locationError = true else onApply(MetadataChanges(location = FieldChange.Set(point)))
                    },
                    enabled = location.isNotBlank(),
                    tone = NeoTone.Pink,
                )
                NeoButton(
                    stringResource(R.string.metadata_remove_location),
                    onClick = { onApply(MetadataChanges(location = FieldChange.Remove)) },
                    tone = NeoTone.Surface,
                )
            }

            NeoCard {
                NeoTag(stringResource(R.string.metadata_texts), tone = NeoTone.Green)
                Text(stringResource(R.string.group_metadata_texts_hint), style = MaterialTheme.typography.bodySmall)
                NeoTextField(stringResource(R.string.metadata_description), description, { description = it }, singleLine = false)
                NeoTextField(stringResource(R.string.metadata_artist), artist, { artist = it })
                NeoTextField(stringResource(R.string.metadata_copyright), copyright, { copyright = it })
                fun change(value: String): FieldChange<String> = if (value.isBlank()) FieldChange.Keep else FieldChange.Set(value)
                NeoButton(
                    stringResource(R.string.group_metadata_apply),
                    onClick = { onApply(MetadataChanges(change(description), change(artist), change(copyright))) },
                    enabled = description.isNotBlank() || artist.isNotBlank() || copyright.isNotBlank(),
                    tone = NeoTone.Pink,
                )
            }

            NeoButton(
                stringResource(R.string.metadata_remove_sensitive),
                onClick = { onApply(MetadataChanges(removeSensitiveData = true)) },
                tone = NeoTone.Pink,
            )
        }
    }
}

/** Récapitulatif avant application : modifiées, déplacées sur le SSD, ignorées. */
@Composable
fun GroupMetadataConfirmDialog(
    preview: MetadataEditPreview?,
    removesSensitiveData: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NeoCard(modifier = Modifier.padding(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.group_metadata_confirm_title), style = MaterialTheme.typography.titleLarge)
                if (preview == null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    val lines = buildList {
                        add(pluralStringResource(R.plurals.group_metadata_modified, preview.editable, preview.editable))
                        if (preview.toMove > 0) add(pluralStringResource(R.plurals.group_metadata_moved, preview.toMove, preview.toMove))
                        if (preview.skipped > 0) add(pluralStringResource(R.plurals.group_metadata_skipped, preview.skipped, preview.skipped))
                    }
                    NeoNotice(lines.joinToString(", "), tone = NeoTone.Yellow)
                    if (removesSensitiveData) {
                        Text(stringResource(R.string.metadata_remove_sensitive_list), style = MaterialTheme.typography.bodySmall)
                    }
                    NeoButton(stringResource(R.string.group_metadata_confirm), onClick = onConfirm, enabled = preview.editable > 0, tone = NeoTone.Pink)
                }
                NeoButton(stringResource(R.string.delete_confirm_cancel), onClick = onDismiss, tone = NeoTone.Surface)
            }
        }
    }
}

@Composable
fun GroupMetadataProgressDialog(current: Int, total: Int) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        NeoCard(modifier = Modifier.padding(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.group_metadata_progress, current, total), style = MaterialTheme.typography.titleMedium)
                NeoProgressBar(progress = if (total == 0) 0f else (current - 1).toFloat() / total)
            }
        }
    }
}
