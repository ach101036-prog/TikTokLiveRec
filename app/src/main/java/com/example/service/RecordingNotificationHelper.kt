package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object RecordingNotificationHelper {

    const val CHANNEL_RECORDING = "recording_active"
    const val CHANNEL_SAVED = "recording_saved"

    const val NOTIFICATION_ID_ONGOING = 1001
    const val NOTIFICATION_ID_SAVED_BASE = 2000

    const val EXTRA_OPEN_RECORDING_ID = "extra_open_recording_id"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            val recordingChannel = NotificationChannel(
                CHANNEL_RECORDING,
                context.getString(R.string.notification_channel_recording),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows elapsed time while recording a live stream"
                setShowBadge(false)
            }

            val savedChannel = NotificationChannel(
                CHANNEL_SAVED,
                context.getString(R.string.notification_channel_saved),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a recorded MP4 file is saved locally"
                enableVibration(true)
            }

            manager.createNotificationChannel(recordingChannel)
            manager.createNotificationChannel(savedChannel)
        }
    }

    fun buildOngoingNotification(
        context: Context,
        username: String,
        elapsedText: String,
        sizeText: String
    ): Notification {
        ensureChannels(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Recording @$username")
            .setContentText("Elapsed $elapsedText • $sizeText")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop & Save",
                stopPendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    fun showSavedNotification(
        context: Context,
        recordingId: Long,
        username: String,
        fileName: String,
        durationText: String,
        sizeText: String
    ) {
        ensureChannels(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val openPlayerIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_RECORDING_ID, recordingId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            recordingId.toInt(),
            openPlayerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SAVED)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.notification_saved_title))
            .setContentText("@$username • $durationText ($sizeText) saved as $fileName")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Saved @$username live stream to $fileName ($durationText, $sizeText). Tap to watch offline."
                )
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        val id = NOTIFICATION_ID_SAVED_BASE + (recordingId.toInt() % 1000)
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
