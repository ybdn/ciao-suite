package dev.ybdn.ciaocloud.presentation.system

import android.app.Activity
import android.app.PendingIntent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Lance les confirmations système de MediaStore (`createDeleteRequest`, `createTrashRequest`) depuis
 * le domaine. Chaque activité s'enregistre au démarrage via [register] ; la dernière au premier plan
 * est utilisée.
 */
class IntentSenderLauncher {

    private var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var pendingContinuation: CancellableContinuation<Boolean>? = null

    /** À appeler dans `onCreate`, avant que l'activité ne soit démarrée. */
    fun register(activity: ComponentActivity) {
        val activityLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result -> onResult(result.resultCode) }
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                launcher = activityLauncher
            }

            override fun onDestroy(owner: LifecycleOwner) {
                if (launcher === activityLauncher) launcher = null
            }
        })
        launcher = activityLauncher
    }

    /** @return true si l'utilisateur a accepté ; false s'il a refusé ou si aucune activité n'est disponible. */
    suspend fun launch(pendingIntent: PendingIntent): Boolean = withContext(Dispatchers.Main) {
        val activeLauncher = launcher ?: return@withContext false
        suspendCancellableCoroutine { continuation ->
            pendingContinuation = continuation
            continuation.invokeOnCancellation { pendingContinuation = null }
            activeLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }

    private fun onResult(resultCode: Int) {
        val continuation = pendingContinuation ?: return
        pendingContinuation = null
        if (continuation.isActive) continuation.resume(resultCode == Activity.RESULT_OK)
    }
}
