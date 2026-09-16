package dev.ybdn.ciaocloud.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.ybdn.ciaocloud.CiaoCloudApplication
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.TransferAbortReason
import dev.ybdn.ciaocloud.domain.model.TransferProgress
import dev.ybdn.ciaocloud.presentation.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orchestrateur technique du transfert : tourne en Foreground Service (notification de
 * progression) pour survivre au passage en arrière-plan sur de gros volumes de données.
 * Transfère la liste issue du dernier scan (`ScanSession`), via les use cases du domaine.
 */
class TransferForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var transferJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotificationAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Obligatoire après chaque startForegroundService(), même si un transfert tourne déjà.
        startForeground(NOTIFICATION_ID, buildProgressNotification(getString(R.string.notification_transfer_preparing)))
        if (transferJob?.isActive != true) {
            startTransfer()
        }
        return START_NOT_STICKY
    }

    private fun startTransfer() {
        val appContainer = (application as CiaoCloudApplication).appContainer
        acquireWakeLock()

        transferJob = serviceScope.launch {
            try {
                val files = appContainer.scanSession.files.value.orEmpty()
                appContainer.transferMediaUseCase(files).collect { progress ->
                    _uiState.update { it.reduce(progress) }
                    updateProgressNotification(_uiState.value)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Transfert interrompu par une erreur inattendue", e)
                _uiState.update {
                    it.copy(isRunning = false, isCompleted = true, fatalError = e.message ?: e::class.simpleName)
                }
            } finally {
                appContainer.scanSession.clear()
                releaseWakeLock()
                // Détacher d'abord, sinon la notification de fin resterait non balayable.
                stopForeground(STOP_FOREGROUND_DETACH)
                showCompletionNotification(_uiState.value)
                stopSelf()
            }
        }
    }

    private fun updateProgressNotification(state: TransferUiState) {
        if (state.isCompleted) return
        // Android limite le débit des notifications : au plus une mise à jour par seconde.
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotificationAt < NOTIFICATION_MIN_INTERVAL_MS) return
        lastNotificationAt = now
        val text = getString(R.string.progress_files_done, state.filesDone, state.totalFiles)
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildProgressNotification(text, state))
    }

    private fun showCompletionNotification(state: TransferUiState) {
        val text = when {
            state.fatalError != null -> getString(R.string.notification_transfer_error)
            state.abortReason is TransferAbortReason.DestinationUnavailable ->
                getString(R.string.transfer_abort_destination_unavailable)
            state.abortReason is TransferAbortReason.InsufficientSpace ->
                getString(R.string.transfer_abort_insufficient_space_short)
            else -> getString(R.string.transfer_summary, state.succeeded, state.failed)
        }
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_transfer_done_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun buildProgressNotification(contentText: String, state: TransferUiState? = null): Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_transfer_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (state != null && state.totalFiles > 0) setProgress(state.totalFiles, state.filesDone, false)
            }
            .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_transfer),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Un Foreground Service n'empêche pas le CPU de dormir écran éteint : sans wake lock, la copie ralentit ou s'arrête. */
    private fun acquireWakeLock() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CiaoCloud:transfer")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releaseWakeLock()
        _uiState.update { if (it.isRunning) it.copy(isRunning = false, isCompleted = true) else it }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TransferService"
        private const val NOTIFICATION_CHANNEL_ID = "ciaocloud_transfer"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_MIN_INTERVAL_MS = 1_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L

        private val _uiState = MutableStateFlow(TransferUiState())
        val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

        /** Démarre un transfert, sauf si un transfert est déjà en cours (dont on garde alors la progression). */
        fun start(context: Context) {
            if (_uiState.value.isRunning) return
            // Réinitialisé ici, de façon synchrone, pour que l'écran ne voie jamais l'état "terminé" du lot précédent.
            _uiState.value = TransferUiState(isRunning = true)
            context.startForegroundService(Intent(context, TransferForegroundService::class.java))
        }
    }
}

data class TransferUiState(
    val isRunning: Boolean = false,
    val currentFileName: String? = null,
    val currentFileSizeBytes: Long = 0,
    val currentFileBytesCopied: Long = 0,
    val filesDone: Int = 0,
    val totalFiles: Int = 0,
    val bytesTransferred: Long = 0,
    val isCompleted: Boolean = false,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val lastErrorFileName: String? = null,
    val lastError: String? = null,
    val abortReason: TransferAbortReason? = null,
    val fatalError: String? = null,
) {
    fun reduce(progress: TransferProgress): TransferUiState = when (progress) {
        is TransferProgress.FileStarted -> copy(
            currentFileName = progress.file.displayName,
            currentFileSizeBytes = progress.file.sizeBytes,
            currentFileBytesCopied = 0,
            filesDone = progress.fileIndex - 1,
            totalFiles = progress.totalFiles,
        )

        is TransferProgress.FileBytesCopied -> copy(currentFileBytesCopied = progress.bytesCopied)

        is TransferProgress.FileVerified -> copy(
            filesDone = progress.fileIndex,
            totalFiles = progress.totalFiles,
            bytesTransferred = progress.bytesTransferredSoFar,
            succeeded = succeeded + 1,
        )

        is TransferProgress.FileFailed -> copy(
            filesDone = progress.fileIndex,
            totalFiles = progress.totalFiles,
            failed = failed + 1,
            lastErrorFileName = progress.file.displayName,
            lastError = progress.reason,
        )

        is TransferProgress.BatchCompleted -> copy(
            isRunning = false,
            isCompleted = true,
            currentFileName = null,
            succeeded = progress.succeeded,
            failed = progress.failed,
            bytesTransferred = progress.totalBytesTransferred,
            abortReason = progress.abortReason,
        )
    }
}
