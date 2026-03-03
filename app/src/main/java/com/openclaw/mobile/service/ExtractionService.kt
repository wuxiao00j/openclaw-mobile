package com.openclaw.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.openclaw.mobile.R
import com.openclaw.mobile.config.SecureStorage
import com.openclaw.mobile.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

@AndroidEntryPoint
class ExtractionService : Service() {

    @Inject
    lateinit var secureStorage: SecureStorage

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private val _extractionState = MutableStateFlow<ExtractionState>(ExtractionState.Idle)
    val extractionState: StateFlow<ExtractionState> = _extractionState

    companion object {
        const val ACTION_START_EXTRACTION = "action_start_extraction"
        const val EXTRA_ASSET_NAME = "extra_asset_name"
        const val EXTRA_TARGET_DIR = "extra_target_dir"
        
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "extraction_channel"
        
        // 广播 Action
        const val ACTION_EXTRACTION_PROGRESS = "action_extraction_progress"
        const val ACTION_EXTRACTION_COMPLETE = "action_extraction_complete"
        const val ACTION_EXTRACTION_ERROR = "action_extraction_error"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_ESTIMATED_SECONDS = "extra_estimated_seconds"
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"
    }

    sealed class ExtractionState {
        object Idle : ExtractionState()
        data class Progress(val percent: Int, val estimatedSeconds: Int) : ExtractionState()
        object Complete : ExtractionState()
        data class Error(val message: String) : ExtractionState()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_EXTRACTION -> {
                val assetName = intent.getStringExtra(EXTRA_ASSET_NAME) ?: return START_NOT_STICKY
                val targetDir = intent.getStringExtra(EXTRA_TARGET_DIR) ?: return START_NOT_STICKY
                startExtraction(assetName, targetDir)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun startExtraction(assetName: String, targetDir: String) {
        startForeground(NOTIFICATION_ID, createProgressNotification(0))

        serviceScope.launch {
            try {
                _extractionState.value = ExtractionState.Progress(0, 30)
                sendProgressBroadcast(0, 30)
                
                extractAsset(assetName, File(targetDir)) { progress, estimatedSeconds ->
                    _extractionState.value = ExtractionState.Progress(progress, estimatedSeconds)
                    sendProgressBroadcast(progress, estimatedSeconds)
                    updateNotification(progress)
                }
                
                secureStorage.markEnvironmentReady()
                _extractionState.value = ExtractionState.Complete
                sendCompleteBroadcast()
                showCompletionNotification()
                
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                
            } catch (e: Exception) {
                Timber.e(e, "Extraction failed")
                _extractionState.value = ExtractionState.Error(e.message ?: "Unknown error")
                sendErrorBroadcast(e.message ?: "Unknown error")
                showErrorNotification(e.message)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun extractAsset(
        assetName: String, 
        targetDir: File,
        onProgress: (Int, Int) -> Unit
    ) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        assets.open(assetName).use { inputStream ->
            ZipInputStream(inputStream).use { zipIn ->
                var entry = zipIn.nextEntry
                var totalSize = 0L
                var extractedSize = 0L
                val startTime = System.currentTimeMillis()
                
                // 先计算总大小
                while (entry != null) {
                    totalSize += entry.compressedSize
                    entry = zipIn.nextEntry
                }
                
                // 重新打开流
                assets.open(assetName).use { inputStream2 ->
                    ZipInputStream(inputStream2).use { zipIn2 ->
                        var entry2 = zipIn2.nextEntry
                        
                        while (entry2 != null) {
                            val file = File(targetDir, entry2.name)
                            
                            if (entry2.isDirectory) {
                                file.mkdirs()
                            } else {
                                file.parentFile?.mkdirs()
                                FileOutputStream(file).use { fos ->
                                    val buffer = ByteArray(8192)
                                    var len: Int
                                    while (zipIn2.read(buffer).also { len = it } > 0) {
                                        fos.write(buffer, 0, len)
                                        extractedSize += len
                                        
                                        val progress = ((extractedSize * 100) / totalSize).toInt()
                                        val elapsed = System.currentTimeMillis() - startTime
                                        val estimatedTotal = if (extractedSize > 0) (elapsed * totalSize / extractedSize) else 0
                                        val remaining = ((estimatedTotal - elapsed) / 1000).toInt().coerceAtLeast(1)
                                        
                                        onProgress(progress.coerceIn(0, 100), remaining)
                                    }
                                }
                                
                                // 设置可执行权限（如果是可执行文件）
                                if (entry2.name.endsWith("/node") || entry2.name.endsWith("/npm")) {
                                    file.setExecutable(true)
                                }
                            }
                            
                            entry2 = zipIn2.nextEntry
                        }
                    }
                }
            }
        }
        
        Timber.i("Extraction complete: $targetDir")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_extraction),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示环境解压进度"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createProgressNotification(progress: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_extraction_title))
            .setContentText(getString(R.string.notification_extraction_progress, progress))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(progress: Int) {
        notificationManager.notify(NOTIFICATION_ID, createProgressNotification(progress))
    }

    private fun showCompletionNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.extraction_complete))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showErrorNotification(errorMessage: String?) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(errorMessage ?: getString(R.string.error_extraction_failed))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 2, notification)
    }

    /**
     * 发送进度广播
     */
    private fun sendProgressBroadcast(progress: Int, estimatedSeconds: Int) {
        val intent = Intent(ACTION_EXTRACTION_PROGRESS).apply {
            putExtra(EXTRA_PROGRESS, progress)
            putExtra(EXTRA_ESTIMATED_SECONDS, estimatedSeconds)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    /**
     * 发送完成广播
     */
    private fun sendCompleteBroadcast() {
        val intent = Intent(ACTION_EXTRACTION_COMPLETE)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    /**
     * 发送错误广播
     */
    private fun sendErrorBroadcast(errorMessage: String) {
        val intent = Intent(ACTION_EXTRACTION_ERROR).apply {
            putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}
