package com.example

import android.app.Application
import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.local.RecordingRepository
import com.example.data.network.TikTokLiveChecker
import com.example.service.RecordingCoordinator
import com.example.service.RecordingNotificationHelper

class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)

    val recordingRepository = RecordingRepository(
        context = context,
        recordingDao = database.recordingDao()
    )
    val preferencesRepository = PreferencesRepository(context)
    val liveChecker = TikTokLiveChecker()
    val recordingCoordinator = RecordingCoordinator()
}

class LiveVaultApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        RecordingNotificationHelper.ensureChannels(this)
    }
}
