package dev.ybdn.ciaocloud.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.ybdn.ciaocloud.CiaoCloudApplication
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.TransferProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrateur technique du transfert : tourne en Foreground Service (notification de
 * progression) pour survivre au passage en arrière-plan sur de gros volumes de données.
 * N'invoque que les use cases du domaine, jamais `data/` directement.
 */
class TransferForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob())
    private var transferJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_TRANSFER && transferJob?.isActive != true) {
            startTransfer()
        }
        return START_NOT_STICKY
    }

    private fun startTransfer() {
        val appContainer = (application as CiaoCloudApplication).appContainer
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_transfer_title)))

        _uiState.value = TransferUiState()

        transferJob = serviceScope.launch {
            val filesToTransfer = appContainer.scanLocalMediaUseCase()
            appContainer.transferMediaUseCase(filesToTransfer).collect { progress ->
                _uiState.value = _uiState.value.reduce(progress)
                updateNotification(_uiState.value)
                if (progress is TransferProgress.BatchCompleted) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
        }
    }

    private fun updateNotification(state: TransferUiState) {
        val text = getString(R.string.progress_files_done, state.filesDone, state.totalFiles)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(contentText: String): Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_transfer_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_transfer),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        transferJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_TRANSFER = "dev.ybdn.ciaocloud.action.START_TRANSFER"
        private const val NOTIFICATION_CHANNEL_ID = "ciaocloud_transfer"
        private const val NOTIFICATION_ID = 1001

        private val _uiState = MutableStateFlow(TransferUiState())
        val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java)
                .setAction(ACTION_START_TRANSFER)
            context.startForegroundService(intent)
        }
    }
}

data class TransferUiState(
    val currentFileName: String? = null,
    val filesDone: Int = 0,
    val totalFiles: Int = 0,
    val bytesTransferred: Long = 0,
    val isCompleted: Boolean = false,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val lastError: String? = null,
) {
    fun reduce(progress: TransferProgress): TransferUiState = when (progress) {
        is TransferProgress.FileStarted -> copy(
            currentFileName = progress.file.displayName,
            filesDone = progress.fileIndex - 1,
            totalFiles = progress.totalFiles,
        )

        is TransferProgress.FileVerified -> copy(
            currentFileName = progress.file.displayName,
            filesDone = progress.fileIndex,
            totalFiles = progress.totalFiles,
            bytesTransferred = progress.bytesTransferredSoFar,
        )

        is TransferProgress.FileFailed -> copy(
            currentFileName = progress.file.displayName,
            filesDone = progress.fileIndex,
            totalFiles = progress.totalFiles,
            lastError = progress.reason,
        )

        is TransferProgress.BatchCompleted -> copy(
            isCompleted = true,
            succeeded = progress.succeeded,
            failed = progress.failed,
            bytesTransferred = progress.totalBytesTransferred,
        )
    }
}
