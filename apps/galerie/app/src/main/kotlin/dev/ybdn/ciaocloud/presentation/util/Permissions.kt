package dev.ybdn.ciaocloud.presentation.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Permissions donnant l'accès complet au MediaStore (photos + vidéos). */
private val FULL_MEDIA_PERMISSIONS = arrayOf(
    Manifest.permission.READ_MEDIA_IMAGES,
    Manifest.permission.READ_MEDIA_VIDEO,
)

/**
 * Permissions à demander pour le scan. Sur Android 14+, `READ_MEDIA_VISUAL_USER_SELECTED` est
 * demandée en plus : sans elle, un accès partiel serait accordé en mode compatibilité, sous forme
 * de `READ_MEDIA_IMAGES` temporaire, et donc indétectable.
 */
val MEDIA_PERMISSIONS: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        FULL_MEDIA_PERMISSIONS + Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    } else {
        FULL_MEDIA_PERMISSIONS
    }

const val NOTIFICATION_PERMISSION = Manifest.permission.POST_NOTIFICATIONS

enum class MediaAccess { FULL, PARTIAL, DENIED }

fun Context.hasPermissions(vararg permissions: String): Boolean =
    permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

fun Context.mediaAccess(): MediaAccess = when {
    hasPermissions(*FULL_MEDIA_PERMISSIONS) -> MediaAccess.FULL
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        hasPermissions(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccess.PARTIAL
    else -> MediaAccess.DENIED
}

/** Ouvre l'écran système des détails de l'app, pour réactiver une permission refusée définitivement. */
fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
