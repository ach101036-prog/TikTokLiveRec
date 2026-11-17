package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.service.RecordingNotificationHelper
import com.example.ui.navigation.LiveVaultAppNav
import com.example.ui.theme.LiveVaultTheme

class MainActivity : ComponentActivity() {

    private var pendingRecordingId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractRecordingId(intent)

        val appContainer = (application as LiveVaultApp).container

        setContent {
            LiveVaultTheme {
                LiveVaultAppNav(
                    container = appContainer,
                    initialRecordingIdToOpen = pendingRecordingId,
                    onConsumedInitialRecordingId = { pendingRecordingId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractRecordingId(intent)
    }

    private fun extractRecordingId(intent: Intent?) {
        val id = intent?.getLongExtra(RecordingNotificationHelper.EXTRA_OPEN_RECORDING_ID, -1L) ?: -1L
        if (id > 0L) {
            pendingRecordingId = id
        }
    }
}
