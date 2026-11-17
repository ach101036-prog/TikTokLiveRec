package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.LiveVaultApp
import com.example.data.local.RecordingEntity
import com.example.data.network.LiveCreatorInfo
import com.example.data.recorder.MediaStoreSaver
import com.example.data.recorder.Mp4StreamRecorder
import com.example.ui.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recordingJob: Job? = null
    private var timerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as LiveVaultApp
        val coordinator = app.container.recordingCoordinator

        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val creator = pendingCreator ?: coordinator.state.value.activeCreator
                pendingCreator = null
                if (creator == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundCompat(creator.username)
                startRecordingFlow(app, creator)
            }
            ACTION_STOP_RECORDING -> {
                coordinator.requestStop()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(username: String) {
        val notification = RecordingNotificationHelper.buildOngoingNotification(
            context = this,
            username = username,
            elapsedText = "00:00:00",
            sizeText = "0 B"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                RecordingNotificationHelper.NOTIFICATION_ID_ONGOING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(RecordingNotificationHelper.NOTIFICATION_ID_ONGOING, notification)
        }
    }

    private fun startRecordingFlow(app: LiveVaultApp, creator: LiveCreatorInfo) {
        recordingJob?.cancel()
        timerJob?.cancel()

        val coordinator = app.container.recordingCoordinator
        coordinator.markConnecting(creator)

        val streamRecorder = Mp4StreamRecorder(applicationContext)
        val timestampTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeUser = creator.username.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileName = "tiktok_${safeUser}_$timestampTag.mp4"
        val tempMp4 = File(cacheDir, "rec_${System.currentTimeMillis()}.mp4")

        val startedElapsed = SystemClock.elapsedRealtime()

        timerJob = serviceScope.launch {
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - startedElapsed
                coordinator.updateTimer(elapsed)
                val current = coordinator.state.value
                if (current.phase is RecordingPhase.Recording || current.phase is RecordingPhase.Connecting) {
                    val ongoing = RecordingNotificationHelper.buildOngoingNotification(
                        context = this@RecordingService,
                        username = creator.username,
                        elapsedText = Formatters.formatDuration(elapsed),
                        sizeText = Formatters.formatBytes(current.bytesWritten)
                    )
                    runCatching {
                        NotificationManagerCompat.from(this@RecordingService)
                            .notify(RecordingNotificationHelper.NOTIFICATION_ID_ONGOING, ongoing)
                    }
                }
                delay(1000L)
            }
        }

        recordingJob = serviceScope.launch {
            try {
                coordinator.markRecordingStarted()
                val ok = streamRecorder.recordToTempMp4(
                    hlsUrl = creator.hlsUrl,
                    flvUrl = creator.flvUrl,
                    outputMp4 = tempMp4,
                    shouldStop = { coordinator.stopFlag.get() },
                    onStatsUpdate = { stats ->
                        coordinator.updateStats(stats.bytesWritten, stats.segmentsProcessed)
                    }
                )

                timerJob?.cancel()
                val totalDurationMs = (SystemClock.elapsedRealtime() - startedElapsed).coerceAtLeast(1000L)

                if (!ok || !tempMp4.exists() || tempMp4.length() < 1024L) {
                    tempMp4.delete()
                    coordinator.markFailed(
                        "No video frames could be captured from @${creator.username}. The stream may have ended or requires a fresh check."
                    )
                    return@launch
                }

                coordinator.markFinalizing()
                val savedFile = MediaStoreSaver.saveMp4ToDevice(
                    context = applicationContext,
                    tempMp4File = tempMp4,
                    desiredFileName = fileName
                )

                val entity = RecordingEntity(
                    username = creator.username,
                    displayName = creator.displayName,
                    avatarUrl = creator.avatarUrl,
                    streamTitle = creator.title,
                    fileName = savedFile.fileName,
                    contentUri = savedFile.contentUri,
                    filePath = savedFile.filePath,
                    durationMs = totalDurationMs,
                    fileSizeBytes = savedFile.fileSizeBytes,
                    qualityLabel = creator.selectedQualityLabel,
                    recordedAtMillis = System.currentTimeMillis()
                )
                val insertedId = app.container.recordingRepository.saveRecording(entity)
                val finalEntity = entity.copy(id = insertedId)

                coordinator.markSaved(finalEntity)

                RecordingNotificationHelper.showSavedNotification(
                    context = applicationContext,
                    recordingId = insertedId,
                    username = creator.username,
                    fileName = savedFile.fileName,
                    durationText = Formatters.formatDuration(totalDurationMs),
                    sizeText = Formatters.formatBytes(savedFile.fileSizeBytes)
                )
            } catch (e: Exception) {
                tempMp4.delete()
                coordinator.markFailed(
                    e.message ?: "Recording interrupted unexpectedly."
                )
            } finally {
                timerJob?.cancel()
                ServiceCompat.stopForeground(this@RecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        timerJob?.cancel()
        recordingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_RECORDING = "com.example.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.action.STOP_RECORDING"

        @Volatile
        private var pendingCreator: LiveCreatorInfo? = null

        fun startRecording(context: Context, creator: LiveCreatorInfo) {
            pendingCreator = creator
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START_RECORDING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }
    }
}
