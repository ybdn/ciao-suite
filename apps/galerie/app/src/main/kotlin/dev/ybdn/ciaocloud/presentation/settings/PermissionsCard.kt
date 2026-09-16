package dev.ybdn.ciaocloud.presentation.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.presentation.components.NeoButton
import dev.ybdn.ciaocloud.presentation.components.NeoCard
import dev.ybdn.ciaocloud.presentation.components.NeoTag
import dev.ybdn.ciaocloud.presentation.components.NeoTone
import dev.ybdn.ciaocloud.presentation.util.MEDIA_LOCATION_PERMISSION
import dev.ybdn.ciaocloud.presentation.util.MEDIA_PERMISSIONS
import dev.ybdn.ciaocloud.presentation.util.MEDIA_READ_PERMISSIONS
import dev.ybdn.ciaocloud.presentation.util.MediaAccess
import dev.ybdn.ciaocloud.presentation.util.NOTIFICATION_PERMISSION
import dev.ybdn.ciaocloud.presentation.util.hasPermissions
import dev.ybdn.ciaocloud.presentation.util.mediaReadAccess
import dev.ybdn.ciaocloud.presentation.util.openAppSettings

private enum class PermissionStatus { GRANTED, PARTIAL, MISSING }

/**
 * État et octroi de toutes les autorisations utiles à l'app. Une permission refusée plusieurs fois
 * n'est plus proposée par Android : le bouton ouvre alors les paramètres système de l'app.
 */
@Composable
fun PermissionsCard(
    destinationUri: Uri?,
    onSelectDestination: () -> Unit,
) {
    val context = LocalContext.current
    // Relecture des états à chaque retour dans l'app (réglage modifié dans les paramètres système).
    var refreshTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refreshTick++
        onPauseOrDispose { }
    }

    val mediaStatus = remember(refreshTick) {
        when (context.mediaReadAccess()) {
            MediaAccess.FULL -> PermissionStatus.GRANTED
            MediaAccess.PARTIAL -> PermissionStatus.PARTIAL
            MediaAccess.DENIED -> PermissionStatus.MISSING
        }
    }
    val locationStatus = remember(refreshTick) { context.hasPermissions(MEDIA_LOCATION_PERMISSION).toStatus() }
    val notificationStatus = remember(refreshTick) { context.hasPermissions(NOTIFICATION_PERMISSION).toStatus() }
    val ssdStatus = remember(refreshTick, destinationUri) {
        (destinationUri != null && context.contentResolver.persistedUriPermissions.any {
            it.uri == destinationUri && it.isReadPermission && it.isWritePermission
        }).toStatus()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        refreshTick++
        val activity = context.findActivity()
        // Accès partiel choisi volontairement (« Sélectionner des photos ») : ce n'est pas un blocage.
        val partialChoice = context.mediaReadAccess() == MediaAccess.PARTIAL
        val blocked = results.filterValues { granted -> !granted }.keys
            .filterNot { partialChoice && (it in MEDIA_READ_PERMISSIONS || it == VISUAL_USER_SELECTED_PERMISSION) }
            .any { permission -> activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) }
        if (blocked) context.openAppSettings()
    }

    fun request(permissions: Array<String>) {
        val missing = permissions.filterNot { context.hasPermissions(it) }
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
    }

    NeoCard {
        NeoTag(stringResource(R.string.permissions_label), tone = NeoTone.Coral)
        Text(stringResource(R.string.permissions_hint), style = MaterialTheme.typography.bodyMedium)

        PermissionRow(
            title = stringResource(R.string.permissions_media),
            description = stringResource(R.string.permissions_media_hint),
            status = mediaStatus,
            onGrant = { request(MEDIA_PERMISSIONS.filterNot { it == MEDIA_LOCATION_PERMISSION }.toTypedArray()) },
        )
        PermissionRow(
            title = stringResource(R.string.permissions_location),
            description = stringResource(R.string.permissions_location_hint),
            status = locationStatus,
            onGrant = { request(arrayOf(MEDIA_LOCATION_PERMISSION)) },
        )
        PermissionRow(
            title = stringResource(R.string.permissions_notifications),
            description = stringResource(R.string.permissions_notifications_hint),
            status = notificationStatus,
            onGrant = { request(arrayOf(NOTIFICATION_PERMISSION)) },
        )
        PermissionRow(
            title = stringResource(R.string.permissions_ssd),
            description = stringResource(R.string.permissions_ssd_hint),
            status = ssdStatus,
            onGrant = onSelectDestination,
        )

        val runtimeMissing = listOf(mediaStatus, locationStatus, notificationStatus).any { it != PermissionStatus.GRANTED }
        if (runtimeMissing) {
            NeoButton(
                text = stringResource(R.string.permissions_grant_all),
                onClick = { request(MEDIA_PERMISSIONS + NOTIFICATION_PERMISSION) },
                tone = NeoTone.Coral,
            )
        }
        NeoButton(
            text = stringResource(R.string.home_open_app_settings),
            onClick = context::openAppSettings,
            tone = NeoTone.Surface,
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    status: PermissionStatus,
    onGrant: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            NeoTag(
                text = stringResource(
                    when (status) {
                        PermissionStatus.GRANTED -> R.string.permissions_status_granted
                        PermissionStatus.PARTIAL -> R.string.permissions_status_partial
                        PermissionStatus.MISSING -> R.string.permissions_status_missing
                    },
                ),
                tone = when (status) {
                    PermissionStatus.GRANTED -> NeoTone.Lime
                    PermissionStatus.PARTIAL -> NeoTone.Yellow
                    PermissionStatus.MISSING -> NeoTone.Brick
                },
            )
        }
        Text(description, style = MaterialTheme.typography.bodySmall)
        if (status != PermissionStatus.GRANTED) {
            NeoButton(text = stringResource(R.string.permissions_grant), onClick = onGrant, tone = NeoTone.Yellow)
        }
    }
}

private fun Boolean.toStatus() = if (this) PermissionStatus.GRANTED else PermissionStatus.MISSING

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val VISUAL_USER_SELECTED_PERMISSION = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
