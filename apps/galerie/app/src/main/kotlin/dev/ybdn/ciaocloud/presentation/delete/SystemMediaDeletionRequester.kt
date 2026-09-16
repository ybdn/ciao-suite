package dev.ybdn.ciaocloud.presentation.delete

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.model.TransferRecord
import dev.ybdn.ciaocloud.domain.repository.MediaDeletionRequester
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

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
        val continuation = pendingContinuation ?: return
        pendingContinuation = null
        if (continuation.isActive) continuation.resume(resultCode == Activity.RESULT_OK)
    }

    override suspend fun requestDelete(records: List<TransferRecord>): Boolean = withContext(Dispatchers.Main) {
        val activeLauncher = launcher ?: return@withContext false
        val urisToDelete = records.map { record ->
            val collection = when (record.mediaType) {
                MediaType.PHOTO -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                MediaType.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            ContentUris.withAppendedId(collection, record.mediaStoreId)
        }
        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, urisToDelete)

        suspendCancellableCoroutine { continuation ->
            pendingContinuation = continuation
            continuation.invokeOnCancellation { pendingContinuation = null }
            activeLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }
}
