package dev.ybdn.ciaocloud.presentation.delete

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import dev.ybdn.ciaocloud.domain.repository.MediaDeletionRequester
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Déclenche `MediaStore.createDeleteRequest()` (API 30+) : l'utilisateur confirme la suppression
 * via une boîte de dialogue système, sans que l'app ait à gérer `RecoverableSecurityException`.
 *
 * Le lanceur d'activité doit être enregistré côté `MainActivity` et injecté via [bindLauncher],
 * car l'API `ActivityResultLauncher` est liée au cycle de vie de l'activité.
 */
class SystemMediaDeletionRequester(
    private val context: Context,
) : MediaDeletionRequester {

    private var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var pendingContinuation: CancellableContinuation<Boolean>? = null

    fun bindLauncher(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        this.launcher = launcher
    }

    fun onDeletionResult(resultCode: Int) {
        pendingContinuation?.resume(resultCode == Activity.RESULT_OK) { _, _, _ -> }
        pendingContinuation = null
    }

    override suspend fun requestDelete(mediaStoreIds: List<Long>): Boolean {
        val activeLauncher = launcher ?: return false
        val urisToDelete = mediaStoreIds.map { id ->
            ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
        }
        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, urisToDelete)

        return suspendCancellableCoroutine { continuation ->
            pendingContinuation = continuation
            activeLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }
}
